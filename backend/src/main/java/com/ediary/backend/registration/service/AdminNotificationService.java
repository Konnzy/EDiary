package com.ediary.backend.registration.service;

import com.ediary.backend.registration.repository.RegistrationRequestRepository;
import com.ediary.backend.registration.mapper.RegistrationRequestMapper;
import com.ediary.backend.registration.dto.response.*;
import com.ediary.backend.registration.entity.RegistrationStatus;
import com.ediary.backend.user.repository.UserRepository;
import com.ediary.backend.user.entity.*;
import com.ediary.backend.common.error.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminNotificationService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final RegistrationRequestRepository requests;
    private final RegistrationRequestMapper mapper;
    @Transactional(readOnly=true)
    public RegistrationNotificationFeedResponse feed(String login) {
        long admin=admin(login);
        var items=new ArrayList<RegistrationRequestResponse>();
        requests.findAllByStatus(RegistrationStatus.PENDING,PageRequest.of(0,5,Sort.by(Sort.Direction.DESC,"createdAt","id"))).map(mapper::toResponse).forEach(items::add);
        if(items.size()<5) {
            var ids=jdbc.queryForList("select r.request_id from registration_request r where r.request_status<>'PENDING' and not exists(select 1 from admin_notification_dismissal d where d.admin_id=? and d.request_id=r.request_id and d.request_status=r.request_status::text) order by r.request_reviewed_at desc nulls last,r.request_id desc limit ?",Long.class,admin,5-items.size());
            ids.forEach(id->requests.findById(id).map(mapper::toResponse).ifPresent(items::add));
        }
        return new RegistrationNotificationFeedResponse(requests.countByStatus(RegistrationStatus.PENDING),List.copyOf(items));
    }
    @Transactional
    public void dismiss(String login,long id) {
        long admin=admin(login);
        var value=requests.findById(id).orElseThrow(()->new ApplicationException(HttpStatus.NOT_FOUND,"REQUEST_NOT_FOUND","Заявку не знайдено"));
        if(value.getStatus()==RegistrationStatus.PENDING)throw new ApplicationException(HttpStatus.CONFLICT,"REQUEST_PENDING","Заявку на розгляді не можна приховати");
        jdbc.update("insert into admin_notification_dismissal(admin_id,request_id,request_status) values (?,?,?) on conflict do nothing",admin,id,value.getStatus().name());
    }
    @Transactional
    public void clear(String login) {
        long admin=admin(login);
        jdbc.update("insert into admin_notification_dismissal(admin_id,request_id,request_status) select ?,request_id,request_status::text from registration_request where request_status<>'PENDING' on conflict do nothing",admin);
    }
    private long admin(String login) {
        var user=users.findByLoginIgnoreCase(login).orElseThrow(()->new ApplicationException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Доступ заборонено"));
        if(user.getRole()!=UserRole.ADMIN||user.getStatus()!=UserStatus.ACTIVE)throw new ApplicationException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Доступ заборонено");
        return user.getId();
    }
}
