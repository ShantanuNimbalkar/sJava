-- ❌ SELECT * usage
SELECT * FROM users;

-- ❌ No semicolon at the end
INSERT INTO users (id, name, email)
VALUES (1, 'Alice', 'alice@example.com')

-- ❌ Hardcoded ID and value
UPDATE users
SET name = 'Bob'
WHERE id = 1;

-- ❌ No WHERE clause in DELETE
DELETE FROM orders;

-- ❌ Inconsistent casing and formatting
select OrderId, TotalAmount from Orders where TotalAmount > 1000;

-- ❌ Table alias not used, unclear joins
SELECT u.id, u.name, o.id, o.total
FROM users u, orders o
WHERE u.id = o.user_id;

-- ❌ Using reserved keyword as column name
CREATE TABLE report (
  id SERIAL PRIMARY KEY,
  "select" TEXT,
  data TEXT
);

-- ❌ No index on foreign key column
CREATE TABLE orders (
  id SERIAL PRIMARY KEY,
  user_id INT,
  amount DECIMAL(10,2),
  FOREIGN KEY (user_id) REFERENCES users(id)
);
