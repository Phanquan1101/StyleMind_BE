-- Dedicated demo project only. Application schemas are created by existing Flyway migrations.
\getenv commerce_password DEMO_COMMERCE_PASSWORD
\getenv stylist_password DEMO_STYLIST_PASSWORD
CREATE ROLE commerce_demo LOGIN PASSWORD :'commerce_password';
CREATE ROLE stylist_demo LOGIN PASSWORD :'stylist_password';
CREATE DATABASE stylemind_commerce OWNER commerce_demo;
CREATE DATABASE stylemind_stylist OWNER stylist_demo;
