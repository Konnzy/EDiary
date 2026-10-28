package com.ediary.backend.notification.service;

import com.ediary.backend.common.dto.PageResponse;
import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.common.audit.AuditService;
import static com.ediary.backend.common.audit.AuditValues.of;
import com.ediary.backend.notification.dto.MessageDtos.*;
import com.ediary.backend.notification.entity.Message;
import com.ediary.backend.notification.repository.*;
import com.ediary.backend.schedule.service.SchedulePeriod;
import com.ediary.backend.user.entity.*;
import com.ediary.backend.user.repository.UserRepository;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messages;
    private final MessageDirectory directory;
    private final UserRepository users;
    private final UserNotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly=true)
    public List<Recipient> recipients(String login, String query, Long id) {
        var actor = actor(login);
        if (query.length()>150) throw invalid();
        return directory.search(actor.getId(),SchedulePeriod.academicYear(LocalDate.now(clock)),query,id);
    }

    @Transactional(readOnly=true)
    public Mailbox list(String login, String folder, int page) {
        var actor = actor(login);
        if (!Set.of("inbox","sent","important").contains(folder) || page<0 || page>100000) throw invalid();
        Specification<Message> spec = (root,q,b) -> folder.equals("sent")
            ? b.equal(root.get("sender").get("id"),actor.getId())
            : folder.equals("important")
                ? b.and(b.equal(root.get("receiver").get("id"),actor.getId()),b.isTrue(root.get("important")))
                : b.equal(root.get("receiver").get("id"),actor.getId());
        var result=messages.findAll(spec,PageRequest.of(page,20,Sort.by(Sort.Direction.DESC,"createdAt","id")));
        var identities=new HashMap<Long,Recipient>();
        return new Mailbox(PageResponse.from(result.map(m->item(m,actor.getId(),identities))),messages.countByReceiver_IdAndReadFalse(actor.getId()));
    }

    @Transactional
    public Item send(String login, Send request) {
        // Serializes retries from this sender, including simultaneous requests with the same UUID.
        var actor = users.findByIdForUpdate(actor(login).getId()).orElseThrow(this::notFound);
        var previous=messages.findBySender_IdAndClientRequestId(actor.getId(),request.clientRequestId());
        if(previous.isPresent()) {
            var value=previous.get();
            if (!value.getReceiver().getId().equals(request.recipientId()) || !value.getSubject().equals(request.subject().trim()) || !value.getText().equals(request.text().trim()))
                throw new ApplicationException(HttpStatus.CONFLICT,"MESSAGE_REQUEST_REUSED","Повторіть надсилання з новим ідентифікатором");
            return item(value,actor.getId(),new HashMap<>());
        }
        if(recipients(login,"",request.recipientId()).isEmpty()) throw new ApplicationException(HttpStatus.FORBIDDEN,"RECIPIENT_NOT_ALLOWED","Цьому користувачу не можна надіслати повідомлення");
        var receiver=users.findById(request.recipientId()).orElseThrow(this::notFound);
        var message=new Message(); message.setSender(actor); message.setReceiver(receiver);
        message.setSubject(request.subject().trim()); message.setText(request.text().trim());
        message.setClientRequestId(request.clientRequestId()); message.setCreatedAt(LocalDateTime.now(clock));
        messages.saveAndFlush(message);
        notifications.messageReceived(receiver,message.getId(),directory.identity(actor.getId()).fullName(),message.getSubject());
        audit.record("MESSAGE_SENT","MESSAGE",message.getId(),of(),of("created",true));
        return item(message,actor.getId(),new HashMap<>());
    }

    @Transactional
    public Item open(String login, Long id) {
        var actor=actor(login); var message=messages.findOwned(id,actor.getId()).orElseThrow(this::notFound);
        if(message.getReceiver().getId().equals(actor.getId())) {
            message.setRead(true);
            notifications.messageRead(actor.getId(),id);
        }
        return item(message,actor.getId(),new HashMap<>());
    }

    @Transactional
    public Item important(String login, Long id, boolean important) {
        var actor=actor(login); var message=messages.findOwned(id,actor.getId()).orElseThrow(this::notFound);
        if(!message.getReceiver().getId().equals(actor.getId())) throw notFound();
        message.setImportant(important); return item(message,actor.getId(),new HashMap<>());
    }

    private Item item(Message m,Long actor,Map<Long,Recipient> identities) {
        return new Item(m.getId(),identities.computeIfAbsent(m.getSender().getId(),directory::identity),
            identities.computeIfAbsent(m.getReceiver().getId(),directory::identity),m.getSubject(),m.getText(),m.getCreatedAt(),
            m.isRead(),m.isImportant(),m.getReceiver().getId().equals(actor));
    }
    private AppUser actor(String login) {
        var value=users.findByLoginIgnoreCase(login).orElseThrow(this::notFound);
        if(value.getStatus()!=UserStatus.ACTIVE || value.getRole()==UserRole.ADMIN) throw new ApplicationException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Доступ до повідомлень відсутній");
        return value;
    }
    private ApplicationException notFound() { return new ApplicationException(HttpStatus.NOT_FOUND,"MESSAGE_NOT_FOUND","Повідомлення не знайдено"); }
    private ApplicationException invalid() { return new ApplicationException(HttpStatus.BAD_REQUEST,"INVALID_MESSAGE_REQUEST","Перевірте параметри запиту"); }
}
