\set ON_ERROR_STOP on

\getenv identity_username STYLEMIND_IDENTITY_DB_USERNAME
SELECT COALESCE((SELECT datdba = (SELECT oid FROM pg_roles WHERE rolname = :'identity_username')
                 FROM pg_database WHERE datname = 'stylemind_identity'), false)
       AND has_database_privilege(:'identity_username', 'stylemind_identity', 'CONNECT')
       AND NOT has_database_privilege(:'identity_username', 'stylemind_commerce', 'CONNECT')
       AND NOT has_database_privilege(:'identity_username', 'stylemind_payment', 'CONNECT')
       AND NOT has_database_privilege(:'identity_username', 'stylemind_wardrobe_community', 'CONNECT')
       AND NOT has_database_privilege(:'identity_username', 'stylemind_stylist', 'CONNECT')
       AND NOT has_database_privilege(:'identity_username', 'stylemind_notification', 'CONNECT')
       AND NOT has_database_privilege(:'identity_username', 'postgres', 'CONNECT') AS identity_ok \gset
\if :identity_ok
\echo identity boundary OK
\else
\echo identity boundary FAILED
\quit 1
\endif

\getenv commerce_username STYLEMIND_COMMERCE_DB_USERNAME
SELECT COALESCE((SELECT datdba = (SELECT oid FROM pg_roles WHERE rolname = :'commerce_username')
                 FROM pg_database WHERE datname = 'stylemind_commerce'), false)
       AND has_database_privilege(:'commerce_username', 'stylemind_commerce', 'CONNECT')
       AND NOT has_database_privilege(:'commerce_username', 'stylemind_identity', 'CONNECT')
       AND NOT has_database_privilege(:'commerce_username', 'stylemind_payment', 'CONNECT')
       AND NOT has_database_privilege(:'commerce_username', 'stylemind_wardrobe_community', 'CONNECT')
       AND NOT has_database_privilege(:'commerce_username', 'stylemind_stylist', 'CONNECT')
       AND NOT has_database_privilege(:'commerce_username', 'stylemind_notification', 'CONNECT')
       AND NOT has_database_privilege(:'commerce_username', 'postgres', 'CONNECT') AS commerce_ok \gset
\if :commerce_ok
\echo commerce boundary OK
\else
\echo commerce boundary FAILED
\quit 1
\endif

\getenv payment_username STYLEMIND_PAYMENT_DB_USERNAME
SELECT COALESCE((SELECT datdba = (SELECT oid FROM pg_roles WHERE rolname = :'payment_username')
                 FROM pg_database WHERE datname = 'stylemind_payment'), false)
       AND has_database_privilege(:'payment_username', 'stylemind_payment', 'CONNECT')
       AND NOT has_database_privilege(:'payment_username', 'stylemind_identity', 'CONNECT')
       AND NOT has_database_privilege(:'payment_username', 'stylemind_commerce', 'CONNECT')
       AND NOT has_database_privilege(:'payment_username', 'stylemind_wardrobe_community', 'CONNECT')
       AND NOT has_database_privilege(:'payment_username', 'stylemind_stylist', 'CONNECT')
       AND NOT has_database_privilege(:'payment_username', 'stylemind_notification', 'CONNECT')
       AND NOT has_database_privilege(:'payment_username', 'postgres', 'CONNECT') AS payment_ok \gset
\if :payment_ok
\echo payment boundary OK
\else
\echo payment boundary FAILED
\quit 1
\endif

\getenv wardrobe_community_username STYLEMIND_WARDROBE_COMMUNITY_DB_USERNAME
SELECT COALESCE((SELECT datdba = (SELECT oid FROM pg_roles WHERE rolname = :'wardrobe_community_username')
                 FROM pg_database WHERE datname = 'stylemind_wardrobe_community'), false)
       AND has_database_privilege(:'wardrobe_community_username', 'stylemind_wardrobe_community', 'CONNECT')
       AND NOT has_database_privilege(:'wardrobe_community_username', 'stylemind_identity', 'CONNECT')
       AND NOT has_database_privilege(:'wardrobe_community_username', 'stylemind_commerce', 'CONNECT')
       AND NOT has_database_privilege(:'wardrobe_community_username', 'stylemind_payment', 'CONNECT')
       AND NOT has_database_privilege(:'wardrobe_community_username', 'stylemind_stylist', 'CONNECT')
       AND NOT has_database_privilege(:'wardrobe_community_username', 'stylemind_notification', 'CONNECT')
       AND NOT has_database_privilege(:'wardrobe_community_username', 'postgres', 'CONNECT') AS wardrobe_community_ok \gset
\if :wardrobe_community_ok
\echo wardrobe-community boundary OK
\else
\echo wardrobe-community boundary FAILED
\quit 1
\endif

\getenv stylist_username STYLEMIND_STYLIST_DB_USERNAME
SELECT COALESCE((SELECT datdba = (SELECT oid FROM pg_roles WHERE rolname = :'stylist_username')
                 FROM pg_database WHERE datname = 'stylemind_stylist'), false)
       AND has_database_privilege(:'stylist_username', 'stylemind_stylist', 'CONNECT')
       AND NOT has_database_privilege(:'stylist_username', 'stylemind_identity', 'CONNECT')
       AND NOT has_database_privilege(:'stylist_username', 'stylemind_commerce', 'CONNECT')
       AND NOT has_database_privilege(:'stylist_username', 'stylemind_payment', 'CONNECT')
       AND NOT has_database_privilege(:'stylist_username', 'stylemind_wardrobe_community', 'CONNECT')
       AND NOT has_database_privilege(:'stylist_username', 'stylemind_notification', 'CONNECT')
       AND NOT has_database_privilege(:'stylist_username', 'postgres', 'CONNECT') AS stylist_ok \gset
\if :stylist_ok
\echo stylist boundary OK
\else
\echo stylist boundary FAILED
\quit 1
\endif

\getenv notification_username STYLEMIND_NOTIFICATION_DB_USERNAME
SELECT COALESCE((SELECT datdba = (SELECT oid FROM pg_roles WHERE rolname = :'notification_username')
                 FROM pg_database WHERE datname = 'stylemind_notification'), false)
       AND has_database_privilege(:'notification_username', 'stylemind_notification', 'CONNECT')
       AND NOT has_database_privilege(:'notification_username', 'stylemind_identity', 'CONNECT')
       AND NOT has_database_privilege(:'notification_username', 'stylemind_commerce', 'CONNECT')
       AND NOT has_database_privilege(:'notification_username', 'stylemind_payment', 'CONNECT')
       AND NOT has_database_privilege(:'notification_username', 'stylemind_wardrobe_community', 'CONNECT')
       AND NOT has_database_privilege(:'notification_username', 'stylemind_stylist', 'CONNECT')
       AND NOT has_database_privilege(:'notification_username', 'postgres', 'CONNECT') AS notification_ok \gset
\if :notification_ok
\echo notification boundary OK
\else
\echo notification boundary FAILED
\quit 1
\endif

SELECT 'PERSISTENCE_BOUNDARIES_OK';
