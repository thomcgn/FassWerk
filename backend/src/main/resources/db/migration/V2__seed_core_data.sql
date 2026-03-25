insert into booking_slot_config (slot_duration_minutes, max_reservation_duration_minutes, no_show_grace_period_minutes, booking_interval_mode, active)
values (30, 180, 15, 'FIXED', true);

insert into opening_hours (weekday, open, open_time, close_time, second_open_time, second_close_time)
values
    ('MONDAY', true, '17:00', '23:00', null, null),
    ('TUESDAY', true, '17:00', '23:00', null, null),
    ('WEDNESDAY', true, '17:00', '23:00', null, null),
    ('THURSDAY', true, '17:00', '23:00', null, null),
    ('FRIDAY', true, '16:00', '23:59', null, null),
    ('SATURDAY', true, '14:00', '23:59', null, null),
    ('SUNDAY', true, '14:00', '22:00', null, null)
on conflict (weekday) do nothing;






