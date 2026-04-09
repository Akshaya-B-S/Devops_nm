-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    active BOOLEAN DEFAULT TRUE
);

-- Create boards table
CREATE TABLE IF NOT EXISTS boards (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    template VARCHAR(50),
    content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create index for performance
CREATE INDEX idx_user_id ON boards(user_id);
CREATE INDEX idx_username ON users(username);
CREATE INDEX idx_email ON users(email);

-- Insert test user (password: test123)
INSERT INTO users (id, username, email, password_hash, created_at) 
VALUES ('test-user-1', 'demo', 'demo@example.com', '$2a$10$rVqPq5qPq5qPq5qPq5qPq5qPq5qPq5qPq5qPq5', CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;