const assert = require('node:assert/strict');
const { test } = require('node:test');
const { projectName, validateConfiguration, verifyTarget, composeEnvironment } = require('./disposable.cjs');

test('sample interpolation keys override ambient values without removing Docker connection settings', () => {
  const ambient = {
    MYSQL_DATABASE: 'synthetic_ambient_database',
    MYSQL_PASSWORD: 'synthetic_ambient_password',
    FISHBOOK_ADMIN_BOOTSTRAP_ENABLED: 'false',
    PATH: '/synthetic/tool/path',
    DOCKER_HOST: 'unix:///synthetic/docker.sock',
  };
  const sample = '# Sample only\nMYSQL_DATABASE=fixture_db\nMYSQL_PASSWORD=fixture_password\nFISHBOOK_ADMIN_BOOTSTRAP_ENABLED=true\n';
  const actual = composeEnvironment(sample, ambient);
  assert.equal(actual.MYSQL_DATABASE, 'fixture_db');
  assert.equal(actual.MYSQL_PASSWORD, 'fixture_password');
  assert.equal(actual.FISHBOOK_ADMIN_BOOTSTRAP_ENABLED, 'true');
  assert.equal(actual.PATH, ambient.PATH);
  assert.equal(actual.DOCKER_HOST, ambient.DOCKER_HOST);
  assert.equal(ambient.MYSQL_DATABASE, 'synthetic_ambient_database');
});

test('rejects sample expansion rather than resolving it from ambient values', () => {
  assert.throws(() => composeEnvironment('MYSQL_PASSWORD=${AMBIENT_PASSWORD}'), /sample environment format/);
});

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
