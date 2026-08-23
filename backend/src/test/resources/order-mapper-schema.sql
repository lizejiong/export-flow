DROP TABLE IF EXISTS orders;

CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    customer_phone VARCHAR(32) NOT NULL,
    order_status VARCHAR(32) NOT NULL,
    payment_status VARCHAR(32) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    order_source VARCHAR(32) NOT NULL,
    item_count INT NOT NULL,
    total_amount DECIMAL(15, 2) NOT NULL,
    shipping_province VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    paid_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL
);

INSERT INTO orders (
    order_no, customer_name, customer_phone, order_status, payment_status, payment_method,
    order_source, item_count, total_amount, shipping_province, created_at, paid_at, updated_at
) VALUES (
    'ORD-TEST-1', '测试用户', '13800000000', 'PENDING_SHIPMENT', 'PAID', 'WECHAT',
    'WEB', 2, 99.50, '上海', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS export_task_item;

CREATE TABLE export_task_item (
    task_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id, order_id)
);

INSERT INTO export_task_item (task_id, order_id) VALUES (77, 1);
