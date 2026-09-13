INSERT INTO admin_activity (
    activity_actor_login, activity_title, activity_target, activity_occurred_at
)
SELECT reviewer.user_login,
       CASE WHEN request.request_status::text = 'APPROVED'
            THEN 'Схвалено заявку' ELSE 'Відхилено заявку' END,
       'Заявка #' || request.request_id,
       request.request_reviewed_at
FROM registration_request request
JOIN App_user reviewer ON reviewer.user_id = request.request_reviewed_by_user_id
WHERE request.request_status::text IN ('APPROVED', 'REJECTED');
