\set ON_ERROR_STOP on

-- Prevent service roles from connecting to shared system databases.
REVOKE ALL ON DATABASE postgres FROM PUBLIC;
REVOKE ALL ON DATABASE template1 FROM PUBLIC;

\getenv identity_username STYLEMIND_IDENTITY_DB_USERNAME
\getenv identity_password STYLEMIND_IDENTITY_DB_PASSWORD
CREATE ROLE :"identity_username" LOGIN PASSWORD :'identity_password';
CREATE DATABASE stylemind_identity OWNER :"identity_username";
REVOKE ALL ON DATABASE stylemind_identity FROM PUBLIC;
GRANT ALL PRIVILEGES ON DATABASE stylemind_identity TO :"identity_username";

\getenv commerce_username STYLEMIND_COMMERCE_DB_USERNAME
\getenv commerce_password STYLEMIND_COMMERCE_DB_PASSWORD
CREATE ROLE :"commerce_username" LOGIN PASSWORD :'commerce_password';
CREATE DATABASE stylemind_commerce OWNER :"commerce_username";
REVOKE ALL ON DATABASE stylemind_commerce FROM PUBLIC;
GRANT ALL PRIVILEGES ON DATABASE stylemind_commerce TO :"commerce_username";

\getenv payment_username STYLEMIND_PAYMENT_DB_USERNAME
\getenv payment_password STYLEMIND_PAYMENT_DB_PASSWORD
CREATE ROLE :"payment_username" LOGIN PASSWORD :'payment_password';
CREATE DATABASE stylemind_payment OWNER :"payment_username";
REVOKE ALL ON DATABASE stylemind_payment FROM PUBLIC;
GRANT ALL PRIVILEGES ON DATABASE stylemind_payment TO :"payment_username";

\getenv wardrobe_community_username STYLEMIND_WARDROBE_COMMUNITY_DB_USERNAME
\getenv wardrobe_community_password STYLEMIND_WARDROBE_COMMUNITY_DB_PASSWORD
CREATE ROLE :"wardrobe_community_username" LOGIN PASSWORD :'wardrobe_community_password';
CREATE DATABASE stylemind_wardrobe_community OWNER :"wardrobe_community_username";
REVOKE ALL ON DATABASE stylemind_wardrobe_community FROM PUBLIC;
GRANT ALL PRIVILEGES ON DATABASE stylemind_wardrobe_community TO :"wardrobe_community_username";

\getenv stylist_username STYLEMIND_STYLIST_DB_USERNAME
\getenv stylist_password STYLEMIND_STYLIST_DB_PASSWORD
CREATE ROLE :"stylist_username" LOGIN PASSWORD :'stylist_password';
CREATE DATABASE stylemind_stylist OWNER :"stylist_username";
REVOKE ALL ON DATABASE stylemind_stylist FROM PUBLIC;
GRANT ALL PRIVILEGES ON DATABASE stylemind_stylist TO :"stylist_username";

\getenv notification_username STYLEMIND_NOTIFICATION_DB_USERNAME
\getenv notification_password STYLEMIND_NOTIFICATION_DB_PASSWORD
CREATE ROLE :"notification_username" LOGIN PASSWORD :'notification_password';
CREATE DATABASE stylemind_notification OWNER :"notification_username";
REVOKE ALL ON DATABASE stylemind_notification FROM PUBLIC;
GRANT ALL PRIVILEGES ON DATABASE stylemind_notification TO :"notification_username";
