---
-- #%L
-- DeviceHive Frontend Logic
-- %%
-- Copyright (C) 2016 DataArt
-- %%
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
-- 
--      http://www.apache.org/licenses/LICENSE-2.0
-- 
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
-- #L%
---
-- 1. Default users
-- admin -> test_admin : admin_pass
INSERT INTO "user" (login, password_hash, password_salt, role, status, login_attempts) VALUES ('test_admin', '+IC4w+NeByiymEWlI5H1xbtNe4YKmPlLRZ7j3xaireg=', '9KynX3ShWnFym4y8Dla039py', 0, 0, 0);

INSERT INTO configuration (name, value, entity_version) VALUES ('session.timeout', '1200000', 0);
INSERT INTO configuration (name, value, entity_version) VALUES ('allowNetworkAutoCreate', 'true', 0);
INSERT INTO configuration (name, value, entity_version) VALUES ('user.login.lastTimeout', '1000', 0);
INSERT INTO configuration (name, value, entity_version) VALUES ('jwt.secret', 'devicehive', 0);

-- 2. Default device classes
INSERT INTO device_class (name, is_permanent, entity_version) VALUES ('Sample VirtualLed Device', FALSE, 0);

-- 3. Default networks
INSERT INTO network (name, description) VALUES ('VirtualLed Sample Network', 'A DeviceHive network for VirtualLed sample');

-- 4. Default devices
INSERT INTO device (guid, name, network_id, device_class_id, blocked) VALUES ('E50D6085-2ABA-48E9-B1C3-73C673E414BE', 'Sample VirtualLed Device', 1, 1, FALSE);
