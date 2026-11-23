package com.ediary.backend.registration.service;

import com.ediary.backend.registration.repository.RegistrationRequestRepository;
import com.ediary.backend.registration.repository.AdminNotificationDismissalRepository;
import com.ediary.backend.registration.mapper.RegistrationRequestMapper;
import com.ediary.backend.registration.dto.response.*;
import com.ediary.backend.registration.entity.RegistrationStatus;
import com.ediary.backend.user.repository.UserRepository;
import com.ediary.backend.user.entity.*;
import com.ediary.backend.common.error.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.time.Clock;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminNotificationService {
    private final AdminNotificationDismissalRepository dismissals;
    private final UserRepository users;
    private final RegistrationRequestRepository requests;
    private final RegistrationRequestMapper mapper;
    private final Clock clock;
    @Transactional(readOnly=true)
    public RegistrationNotificationFeedResponse feed(String login) {
        long admin=admin(login, false);
        var items=new ArrayList<RegistrationRequestResponse>();
        requests.findAllByStatus(RegistrationStatus.PENDING,PageRequest.of(0,5,Sort.by(Sort.Direction.DESC,"createdAt","id"))).map(mapper::toResponse).forEach(items::add);
        if(items.size()<5) {
            requests.findReviewedNotDismissed(admin, RegistrationStatus.PENDING, PageRequest.of(0, 5-items.size()))
                .stream().map(mapper::toResponse).forEach(items::add);
        }
        return new RegistrationNotificationFeedResponse(requests.countByStatus(RegistrationStatus.PENDING),List.copyOf(items));
    }
    @Transactional
    public void dismiss(String login,long id) {
        long admin=admin(login, true);
        var value=requests.findById(id).orElseThrow(()->new ApplicationException(HttpStatus.NOT_FOUND,"REQUEST_NOT_FOUND","Заявку не знайдено"));
        if(value.getStatus()==RegistrationStatus.PENDING)throw new ApplicationException(HttpStatus.CONFLICT,"REQUEST_PENDING","Заявку на розгляді не можна приховати");
        dismissals.dismissReviewed(admin, id, RegistrationStatus.PENDING, clock.instant());
    }
    @Transactional
    public void clear(String login) {
        long admin=admin(login, true);
        dismissals.dismissReviewed(admin, null, RegistrationStatus.PENDING, clock.instant());
    }
    private long admin(String login, boolean lock) {
        var found = lock ? users.findByLoginForUpdate(login) : users.findByLoginIgnoreCase(login);
        var user=found.orElseThrow(()->new ApplicationException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Доступ заборонено"));
        if(user.getRole()!=UserRole.ADMIN||user.getStatus()!=UserStatus.ACTIVE)throw new ApplicationException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Доступ заборонено");
        return user.getId();
    }
}
