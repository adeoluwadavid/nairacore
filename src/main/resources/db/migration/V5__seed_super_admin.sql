INSERT INTO auth.users (
    id,
    first_name,
    last_name,
    email,
    phone_number,
    password_hash,
    role,
    is_active,
    created_at,
    updated_at
) VALUES (
             gen_random_uuid(),
             'Super',
             'Admin',
             'admin@nairacore.com',
             '+2348000000001',
             '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
             'ADMIN',
             true,
             NOW(),
             NOW()
         );