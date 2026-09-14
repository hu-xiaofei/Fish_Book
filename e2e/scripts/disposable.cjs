const { execFileSync, spawnSync } = require('node:child_process');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const e2e = path.resolve(__dirname, '..');
const root = path.resolve(e2e, '..');

function projectName(value) {
  if (!value || !/^fishbook-admin-photo-acceptance-[a-z0-9-]+$/.test(value)) {
    throw new Error('An explicit disposable project named fishbook-admin-photo-acceptance-… is required');
  }
  return value;
}

function verifyTarget(value, read = execFileSync) {
  const project = projectName(value);
  const container = JSON.parse(read('docker', ['inspect', `${project}-frontend-1`], { encoding: 'utf8' }))[0];
  if (container?.Config?.Labels?.['com.docker.compose.project'] !== project) {
    throw new Error('Disposable frontend project label mismatch');
  }
  const ports = container?.NetworkSettings?.Ports?.['8080/tcp'];
  if (ports?.length !== 1 || ports[0].HostIp !== '127.0.0.1' || !/^\d+$/.test(ports[0].HostPort)) {
    throw new Error('Disposable frontend must publish exactly one IPv4 loopback port');
  }
  return `http://127.0.0.1:${ports[0].HostPort}`;
}

function validateConfiguration(value, config) {
  const project = projectName(value);
  if (config.name !== project) throw new Error('Resolved project mismatch');
  for (const name of ['mysql-data', 'minio-data']) {
    if (config.volumes?.[name]?.name !== `${project}_${name}` || config.volumes[name].external) {
      throw new Error('Resolved disposable volume mismatch');
    }
  }
  if (config.networks?.default?.name !== `${project}_default` || config.networks.default.external) {
    throw new Error('Resolved disposable network mismatch');
  }
  for (const service of ['mysql', 'minio', 'backend']) {
    if (!config.services?.[service] || config.services[service].ports?.length) {
      throw new Error('Disposable database and storage must have no published host ports');
    }
  }
  const ports = config.services?.frontend?.ports;
  if (ports?.length !== 1 || ports[0].host_ip !== '127.0.0.1' || ports[0].target !== 8080) {
    throw new Error('Resolved frontend must publish only IPv4 loopback');
  }
}

function composeArgs(project) {
  return ['compose', '--project-name', projectName(project), '--env-file', path.join(root, '.env.example'),
    '-f', path.join(root, 'compose.yaml'), '-f', path.join(root, 'compose.full.yaml'),
    '-f', path.join(e2e, 'compose.disposable.yaml')];
}

function tests(project, args) {
  // Suite-wide preflight runs before Playwright imports ANY test or opens a page.
  const baseURL = verifyTarget(project);
  const result = spawnSync(process.execPath, [path.join(e2e, 'node_modules/@playwright/test/cli.js'), 'test', ...args], {
    cwd: e2e, stdio: 'inherit',
    env: { ...process.env, FISHBOOK_E2E_DISPOSABLE_PROJECT: project, FISHBOOK_E2E_BASE_URL: baseURL },
  });
  if (result.error) throw result.error;
  return result.status ?? 1;
}

function main(command, args) {
  if (command === 'test') return tests(projectName(process.env.FISHBOOK_E2E_DISPOSABLE_PROJECT), args);
  if (!['run', 'config'].includes(command)) throw new Error('Use test, run, or config');
  const project = projectName(process.env.FISHBOOK_E2E_DISPOSABLE_PROJECT
    || `fishbook-admin-photo-acceptance-${Date.now()}-${process.pid}`);
  const compose = composeArgs(project);
  // --env-file alone loses to exported variables. Override only keys explicitly
  // declared in the trusted sample, keeping PATH and Docker connection settings.
  const composeEnv = composeEnvironment(readFileSync(path.join(root, '.env.example'), 'utf8'));
  const config = JSON.parse(execFileSync('docker', [...compose, 'config', '--format', 'json'], { encoding: 'utf8', cwd: root, env: composeEnv }));
  validateConfiguration(project, config);
  console.log(`Disposable project: ${project}; sample settings; dynamic loopback frontend; private database/storage`);
  if (command === 'config') return 0;

  // Never recreate/reuse an existing project or collide with its data volumes.
  for (const probe of [
    ['ps', '-aq', '--filter', `label=com.docker.compose.project=${project}`],
    ['volume', 'ls', '-q', '--filter', `name=${project}`],
    ['network', 'ls', '-q', '--filter', `name=${project}`],
  ]) {
    if (execFileSync('docker', probe, { encoding: 'utf8' }).trim()) {
      throw new Error('Disposable project resources already exist; refusing to recreate or delete them');
    }
  }
  try {
    execFileSync('docker', [...compose, 'up', '--build', '--detach', '--wait', '--wait-timeout', '180'], { stdio: 'inherit', cwd: root, env: composeEnv });
    return tests(project, args);
  } finally {
    // Only reached after name/config/absence checks authorized this new project.
    execFileSync('docker', [...compose, 'down', '--volumes'], { stdio: 'inherit', cwd: root, env: composeEnv });
  }
}

function composeEnvironment(sample, ambient = process.env) {
  const env = { ...ambient };
  for (const raw of sample.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const assignment = /^([A-Z][A-Z0-9_]*)=([^\r\n]*)$/.exec(line);
    // The checked-in sample uses literal unquoted values. Fail closed if it
    // starts requiring dotenv interpolation/quoting this reader cannot match.
    if (!assignment || /[\s'"$#]/.test(assignment[2])) {
      throw new Error('Unsupported sample environment format; expected literal KEY=value');
    }
    env[assignment[1]] = assignment[2];
  }
  return env;
}

module.exports = { projectName, validateConfiguration, verifyTarget, composeEnvironment };
if (require.main === module) {
  try { process.exitCode = main(process.argv[2], process.argv.slice(3)); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
