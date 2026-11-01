-- Reference data: permissions, roles and their mapping (identical for every database vendor).

INSERT INTO permissions (name, description) VALUES ('article:read', 'Read published articles and own drafts');
INSERT INTO permissions (name, description) VALUES ('article:write', 'Create articles and edit/delete own articles');
INSERT INTO permissions (name, description) VALUES ('article:publish', 'Publish own articles');
INSERT INTO permissions (name, description) VALUES ('article:manage', 'Edit, publish and delete any article');
INSERT INTO permissions (name, description) VALUES ('user:manage', 'Manage users and role assignments');
INSERT INTO permissions (name, description) VALUES ('audit:read', 'Query the audit log');
INSERT INTO permissions (name, description) VALUES ('mcp:write', 'Call write tools over MCP');

INSERT INTO roles (name, description) VALUES ('ROLE_ADMIN', 'Administrator: full access');
INSERT INTO roles (name, description) VALUES ('ROLE_EDITOR', 'Editor: write and publish articles, MCP write tools');
INSERT INTO roles (name, description) VALUES ('ROLE_USER', 'User: read articles, write own drafts');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'ROLE_ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_EDITOR' AND p.name IN ('article:read', 'article:write', 'article:publish', 'mcp:write');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_USER' AND p.name IN ('article:read', 'article:write');
