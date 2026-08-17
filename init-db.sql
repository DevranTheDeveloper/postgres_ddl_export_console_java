-- PostgreSQL DDL Studio - Demo Dataset Initialization

CREATE TABLE IF NOT EXISTS public.customers (
    customer_id SERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(200) UNIQUE NOT NULL,
    phone VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.products (
    product_id SERIAL PRIMARY KEY,
    product_name VARCHAR(150) NOT NULL,
    sku VARCHAR(50) UNIQUE NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL,
    stock_quantity INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS public.orders (
    order_id SERIAL PRIMARY KEY,
    customer_id INT REFERENCES public.customers(customer_id) ON DELETE CASCADE,
    total_amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(50) DEFAULT 'COMPLETED',
    ordered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.order_items (
    item_id SERIAL PRIMARY KEY,
    order_id INT REFERENCES public.orders(order_id) ON DELETE CASCADE,
    product_id INT REFERENCES public.products(product_id),
    quantity INT NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS public.payments (
    payment_id SERIAL PRIMARY KEY,
    order_id INT REFERENCES public.orders(order_id) ON DELETE CASCADE,
    payment_method VARCHAR(50) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    paid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE VIEW public.active_customer_orders AS
SELECT o.order_id, c.full_name, c.email, o.total_amount, o.status, o.ordered_at
FROM public.orders o
JOIN public.customers c ON o.customer_id = c.customer_id;

-- Sample Seed Data
INSERT INTO public.customers (full_name, email, phone) VALUES
('Ahmet Yılmaz', 'ahmet@example.com', '+90 555 111 2233'),
('Ayşe Kaya', 'ayse@example.com', '+90 555 444 5566')
ON CONFLICT (email) DO NOTHING;

INSERT INTO public.products (product_name, sku, unit_price, stock_quantity) VALUES
('PostgreSQL DDL Studio Pro', 'PG-DDL-001', 49.99, 100),
('Database Performance Guide', 'BOOK-DB-002', 19.99, 50)
ON CONFLICT (sku) DO NOTHING;
