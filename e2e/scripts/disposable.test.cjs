const assert = require('node:assert/strict');
const { test } = require('node:test');
const { projectName, validateConfiguration, verifyTarget } = require('./disposable.cjs');

test('rejects the long-lived project before Docker or any browser test can run', () => {
  let called = false;
  for (const value of [undefined, '', 'fishbook', 'fishbook-admin-photo-acceptance-../fishbook']) {
    assert.throws(() => verifyTarget(value, () => { called = true; }), /disposable project/);
  }
  assert.equal(called, false);
});

test('accepts only an explicitly isolated project name', () => {
  assert.equal(projectName('fishbook-admin-photo-acceptance-ci-123'), 'fishbook-admin-photo-acceptance-ci-123');
});

test('derives the browser URL only from the labeled loopback frontend', () => {
  const project = 'fishbook-admin-photo-acceptance-test';
  const inspect = [{ Config: { Labels: { 'com.docker.compose.project': project } }, NetworkSettings: { Ports: { '8080/tcp': [{ HostIp: '127.0.0.1', HostPort: '18080' }] } } }];
  const read = () => JSON.stringify(inspect);
  assert.equal(verifyTarget(project, read), 'http://127.0.0.1:18080');
  inspect[0].NetworkSettings.Ports['8080/tcp'][0].HostIp = '0.0.0.0';
  assert.throws(() => verifyTarget(project, read), /loopback/);
  inspect[0].NetworkSettings.Ports['8080/tcp'][0].HostIp = '127.0.0.1';
  inspect[0].Config.Labels['com.docker.compose.project'] = 'fishbook';
  assert.throws(() => verifyTarget(project, read), /label/);
});

test('rejects resolved Compose using a shared volume or published database', () => {
  const project = 'fishbook-admin-photo-acceptance-test';
  const config = {
    name: project,
    services: { mysql: {}, minio: {}, backend: {}, frontend: { ports: [{ host_ip: '127.0.0.1', target: 8080 }] } },
    volumes: { 'mysql-data': { name: `${project}_mysql-data` }, 'minio-data': { name: `${project}_minio-data` } },
    networks: { default: { name: `${project}_default` } },
  };
  assert.doesNotThrow(() => validateConfiguration(project, config));
  config.volumes['mysql-data'].name = 'fishbook_mysql-data';
  assert.throws(() => validateConfiguration(project, config), /volume/);
  config.volumes['mysql-data'].name = `${project}_mysql-data`;
  config.services.mysql.ports = [{ target: 3306, published: '3306' }];
  assert.throws(() => validateConfiguration(project, config), /database|storage/);
});
