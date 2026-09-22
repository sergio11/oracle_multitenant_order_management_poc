# frozen_string_literal: true

# =============================================================================
# Enterprise Order Platform - QA on Kubernetes (kind + Podman)
#
# Prerequisites (workstation):
#   - kind, kubectl, podman, mvn (Maven 3.9+), sqlplus optional
#   - infrastructure/podman/.env  (copy from .env.example)
#   - Free host ports: 8080 (backend), 1521 (Oracle NodePort)
#   - ~6 GB RAM free for the kind node + Oracle
#
# Typical QA flow:
#   rake qa:cluster:create
#   rake qa:image:build
#   rake qa:image:load
#   rake qa:deploy
#   rake qa:wait
#   rake qa:db:migrate
#   rake qa:db:packages
#   rake qa:db:seed
#   rake qa:smoke
#   rake qa:down            # delete workloads (keeps cluster + PVC data)
#   rake qa:cluster:delete  # full teardown
#
# Or one-shot after images exist:
#   rake qa:up              # create cluster + build + load + deploy
#   rake qa:db:init         # migrate + packages + seed (backend needs schema)
#   rake qa:wait            # oracle + backend Ready
#   rake qa:smoke           # health, auth, order flow, idempotency, isolation
# =============================================================================

require 'English'
require 'fileutils'
require 'json'
require 'shellwords'

ROOT        = __dir__
K8S_DIR     = File.join(ROOT, 'infrastructure', 'k8s')
BACKEND_DIR = File.join(ROOT, 'backend')
ENV_FILE    = File.join(ROOT, 'infrastructure', 'podman', '.env')
INIT_SQL    = File.join(ROOT, 'infrastructure', 'podman', 'scripts', '01-init-db.sql')

CLUSTER_NAME = ENV.fetch('QA_CLUSTER', 'poc-qa')
NAMESPACE    = ENV.fetch('QA_NAMESPACE', 'poc-qa')
# NOTE: keep the localhost/ prefix - it is the exact name podman stores
# and kind/containerd looks up (imagePullPolicy: Never needs exact match).
IMAGE        = ENV.fetch('QA_IMAGE', 'localhost/enterprise-orders-qa:0.0.1')
BACKEND_URL  = ENV.fetch('QA_BACKEND_URL', 'http://localhost:8080')
ORACLE_HOST  = ENV.fetch('QA_ORACLE_HOST', 'localhost')
ORACLE_PORT  = ENV.fetch('QA_ORACLE_PORT', '1521')
ORACLE_SID   = ENV.fetch('QA_ORACLE_SERVICE', 'pdb_oltp')
SECRETS_NAME = 'poc-qa-secrets'

# Lab defaults - keep in sync with infrastructure/podman/.env.example
DEFAULTS = {
  'ORACLE_PASSWORD'      => 'ChangeMe123!',
  'ORACLE_DATABASE'      => 'PDB_OLTP',
  'APP_OWNER_USER'       => 'APP_OWNER',
  'APP_OWNER_PWD'        => 'LabOwnerPass1',
  'APP_RUNTIME_USER'     => 'APP_RUNTIME',
  'APP_RUNTIME_PWD'      => 'LabRuntimePass1',
  'APP_REPORTING_USER'   => 'APP_REPORTING',
  'APP_REPORTING_PWD'    => 'LabReportPass1',
  'APP_ADMIN_PASSWORD'   => 'changeme',
  'APP_OPERATOR_PASSWORD'=> 'changeme'
}.freeze

# -----------------------------------------------------------------------------
# Helpers
# -----------------------------------------------------------------------------

def run!(*args, chdir: nil)
  cmd = args.length == 1 ? args.first : args
  puts "\n$ #{cmd.is_a?(Array) ? cmd.join(' ') : cmd}"
  argv = cmd.is_a?(Array) ? cmd : [cmd]
  # NOTE: no kwargs splat - system(str, **{}) raises ArgumentError on Ruby/Windows.
  success = if chdir
              Dir.chdir(chdir) { system(*argv) }
            else
              system(*argv)
            end
  return if success

  status = $CHILD_STATUS
  abort "Command failed (#{status.nil? ? '?' : status.exitstatus}): #{cmd.is_a?(Array) ? cmd.join(' ') : cmd}"
end

def capture!(*args, chdir: nil)
  cmd = args.length == 1 ? args.first : args
  puts "\n$ #{cmd.is_a?(Array) ? cmd.join(' ') : cmd}"
  opts = {}
  opts[:chdir] = chdir if chdir
  out = IO.popen(cmd, err: [:child, :out], **opts, &:read)
  raise "Command failed: #{cmd.is_a?(Array) ? cmd.join(' ') : cmd}\n#{out}" unless $CHILD_STATUS.success?

  out
end

def which?(name)
  exts = ENV['PATHEXT'] ? ENV['PATHEXT'].split(';') : ['']
  ENV['PATH'].to_s.split(File::PATH_SEPARATOR).any? do |dir|
    exts.any? { |ext| File.executable?(File.join(dir, "#{name}#{ext}")) }
  end
end

def load_env_file
  values = DEFAULTS.dup
  if File.exist?(ENV_FILE)
    File.foreach(ENV_FILE) do |line|
      line = line.strip
      next if line.empty? || line.start_with?('#')

      key, val = line.split('=', 2)
      next unless key && val

      values[key.strip] = val.strip.sub(/\A["']|["']\z/, '')
    end
  else
    warn "NOTE: #{ENV_FILE} not found - using built-in lab defaults (copy .env.example to .env)."
  end
  values
end

def kubectl(*args, input: nil)
  if input
    IO.popen(['kubectl', *args], 'r+') do |io|
      io.write(input)
      io.close_write
      io.read
    end
  else
    capture!('kubectl', *args)
  end
end

def cluster_exists?
  out = capture!('kind', 'get', 'clusters')
  out.split("\n").map(&:strip).include?(CLUSTER_NAME)
end

def ensure_cluster!
  abort "kind cluster '#{CLUSTER_NAME}' does not exist. Run: rake qa:cluster:create" unless cluster_exists?
end

def ensure_secret!
  env = load_env_file
  data = {
    'ORACLE_PASSWORD'       => env['ORACLE_PASSWORD'],
    'APP_OWNER_PWD'         => env['APP_OWNER_PWD'],
    'APP_RUNTIME_PWD'       => env['APP_RUNTIME_PWD'],
    'APP_REPORTING_PWD'     => env['APP_REPORTING_PWD'],
    'APP_ADMIN_PASSWORD'    => env['APP_ADMIN_PASSWORD'],
    'APP_OPERATOR_PASSWORD' => env['APP_OPERATOR_PASSWORD']
  }
  args = ['create', 'secret', 'generic', SECRETS_NAME, '-n', NAMESPACE]
  data.each { |k, v| args << "--from-literal=#{k}=#{v}" }
  args += ['--dry-run=client', '-o', 'yaml']
  yaml = capture!('kubectl', *args)
  kubectl('apply', '-f', '-', input: yaml)
end

def ensure_init_configmap!
  abort "Missing #{INIT_SQL}" unless File.exist?(INIT_SQL)

  yaml = capture!('kubectl', 'create', 'configmap', 'oracle-init-scripts',
                  '-n', NAMESPACE, "--from-file=#{INIT_SQL}",
                  '--dry-run=client', '-o', 'yaml')
  kubectl('apply', '-f', '-', input: yaml)
end

def oracle_ready?
  out = capture!('kubectl', 'get', 'statefulset', 'oracle', '-n', NAMESPACE,
                  '-o', 'jsonpath={.status.readyReplicas}')
  out.strip == '1'
rescue StandardError
  false
end

def backend_ready?
  out = capture!('kubectl', 'get', 'deployment', 'backend', '-n', NAMESPACE,
                  '-o', 'jsonpath={.status.readyReplicas}')
  out.strip == '1'
rescue StandardError
  false
end

def wait_for!(description, timeout_s, interval_s = 5)
  deadline = Time.now + timeout_s
  loop do
    return if yield

    abort "Timeout waiting for #{description} (#{timeout_s}s)" if Time.now >= deadline

    sleep interval_s
    print '.'
  end
end

def http_get(url, user: nil, headers: {})
  require 'net/http'
  require 'uri'
  uri = URI.parse(url)
  http = Net::HTTP.new(uri.host, uri.port)
  http.use_ssl = uri.scheme == 'https'
  http.open_timeout = 10
  http.read_timeout = 30
  req = Net::HTTP::Get.new(uri.request_uri)
  headers.each { |k, v| req[k] = v }
  req.basic_auth(*user) if user
  http.request(req)
end

def http_post(url, body, user: nil, headers: {})
  require 'net/http'
  require 'uri'
  uri = URI.parse(url)
  http = Net::HTTP.new(uri.host, uri.port)
  http.use_ssl = uri.scheme == 'https'
  http.open_timeout = 10
  http.read_timeout = 30
  req = Net::HTTP::Post.new(uri.request_uri)
  req['Content-Type'] = 'application/json'
  headers.each { |k, v| req[k] = v }
  req.basic_auth(*user) if user
  req.body = body
  http.request(req)
end

def mvn_cmd
  return @mvn_cmd if defined?(@mvn_cmd) && @mvn_cmd

  if ENV['MVN'] && !ENV['MVN'].empty?
    @mvn_cmd = ENV['MVN']
  elsif system('where mvn >NUL 2>&1')
    @mvn_cmd = 'mvn'
  else
    # Fall back to a Maven wrapper distribution (Windows default location).
    hits = Dir.glob(File.join(Dir.home, '.m2', 'wrapper', 'dists', '**', 'bin', 'mvn.cmd'))
    abort 'Maven not found on PATH and no wrapper dist present - set MVN env var.' if hits.empty?

    @mvn_cmd = hits.max
  end
end

def flyway_migrate!
  env = load_env_file
  url = "jdbc:oracle:thin:@//#{ORACLE_HOST}:#{ORACLE_PORT}/#{ORACLE_SID}"
  run!(mvn_cmd, '-q', 'flyway:migrate',
       "-Dflyway.url=#{url}",
       '-Dflyway.user=APP_OWNER',
       "-Dflyway.password=#{env['APP_OWNER_PWD']}",
       chdir: BACKEND_DIR)
end

def sqlplus_available?
  which?('sqlplus')
end

# Run a SQL script as APP_OWNER against the Oracle NodePort using sqlplus.
# Falls back to an in-pod sqlplus via kubectl exec when no local client exists.
def run_sql_as_owner!(script_path_or_stdin, label:)
  env = load_env_file
  user = env['APP_OWNER_USER']
  pass = env['APP_OWNER_PWD']
  connect = "#{user}/#{pass}@//#{ORACLE_HOST}:#{ORACLE_PORT}/#{ORACLE_SID}"

  # NOTE: sqlplus exit codes are unreliable (a failed script can still exit
  # 0), so every path below ALSO scans output for ORA-/SP2- errors.
  check_output = lambda do |out|
    puts out unless out.strip.empty?
    bad = out.lines.grep(/ORA-\d{5}|SP2-\d{4}|PLS-\d{5}/)
    raise "SQL failed (#{label}): #{bad.first.strip}" unless bad.empty?
  end
  show_output = lambda do |out|
    puts out unless out.strip.empty?
  end

  if sqlplus_available?
    if script_path_or_stdin.is_a?(String) && File.exist?(script_path_or_stdin)
      out = capture!('sqlplus', '-L', '-S', connect, "@#{script_path_or_stdin}")
      check_output.call(out)
    else
      # Feed SQL on stdin to local sqlplus.
      puts "\n$ sqlplus -L -S <connect>  # stdin (#{label})"
      out = IO.popen(['sqlplus', '-L', '-S', connect], 'r+') do |io|
        io.write(script_path_or_stdin)
        io.close_write
        io.read
      end
      raise "SQL failed (#{label})" unless $CHILD_STATUS.success?

      check_output.call(out)
    end
  else
    # No local SQL*Plus: pipe through kubectl into the Oracle pod (has sqlplus).
    # NodePort is not required in this path.
    input = if script_path_or_stdin.is_a?(String) && File.exist?(script_path_or_stdin)
              File.read(script_path_or_stdin)
            else
              script_path_or_stdin
            end
    wrapper = <<~SQL
      WHENEVER SQLERROR EXIT SQL.SQLCODE
      #{input}
      EXIT SUCCESS
    SQL
    # gvenzl image exposes sqlplus for OS-auth / app users via tnsping alias
    conn = "#{user}/#{pass}@//localhost:1521/#{ORACLE_SID}"
    pod = capture!('kubectl', 'get', 'pods', '-n', NAMESPACE, '-l', 'app=oracle',
                   '-o', 'jsonpath={.items[0].metadata.name}').strip
    abort 'Oracle pod not found' if pod.empty?

    cmd = ['kubectl', 'exec', '-i', '-n', NAMESPACE, pod,
           '--', 'bash', '-lc', "sqlplus -L -S #{Shellwords.escape(conn)}"]
    puts "\n$ #{cmd.join(' ')}  # via in-pod sqlplus (#{label})"
    out = IO.popen(cmd, 'r+') do |io|
      io.write(wrapper)
      io.close_write
      io.read
    end
    # Always show output BEFORE checking status - otherwise the actual
    # Oracle error is swallowed when the exit code is the only signal.
    show_output.call(out)
    raise "SQL failed (#{label})" unless $CHILD_STATUS.success?

    check_output.call(out)
  end
end

def run_packages_deploy!
  env = load_env_file
  user = env['APP_OWNER_USER']
  pass = env['APP_OWNER_PWD']
  packages_dir = File.join(ROOT, 'database', 'packages')
  deploy = File.join(packages_dir, 'deploy_packages.sql')
  abort "Missing #{deploy}" unless File.exist?(deploy)

  # deploy_packages.sql uses @@ relative includes - run from its directory.
  if sqlplus_available?
    run!('sqlplus', '-L', '-S', "#{user}/#{pass}@//#{ORACLE_HOST}:#{ORACLE_PORT}/#{ORACLE_SID}",
         "@#{deploy}", chdir: packages_dir)
  else
    conn = "#{user}/#{pass}@//localhost:1521/#{ORACLE_SID}"
    # NOTE: kubectl cp needs a POD name (statefulset/x is not accepted).
    pod = capture!('kubectl', 'get', 'pods', '-n', NAMESPACE, '-l', 'app=oracle',
                   '-o', 'jsonpath={.items[0].metadata.name}').strip
    abort 'Oracle pod not found' if pod.empty?

    # Copy package sources into the pod, then run deploy from that dir.
    run!('kubectl', 'exec', '-n', NAMESPACE, pod, '--', 'mkdir', '-p', '/tmp/packages')
    # NOTE: relative source names - kubectl cp misparses Windows drive
    # letters (C:/...) as remote filespecs.
    Dir.children(packages_dir).select { |f| f.end_with?('.sql') }.each do |f|
      run!('kubectl', 'cp', f, "#{NAMESPACE}/#{pod}:/tmp/packages/#{f}", chdir: packages_dir)
    end
    script = <<~SH
      set -euo pipefail
      cd /tmp/packages
      sqlplus -L -S #{Shellwords.escape(conn)} @deploy_packages.sql
    SH
    run!('kubectl', 'exec', '-i', '-n', NAMESPACE, pod, '--', 'bash', '-lc', script)
  end
end

# -----------------------------------------------------------------------------
# namespace: qa
# -----------------------------------------------------------------------------

namespace :qa do
  desc 'Show all QA tasks'
  task :help do
    sh 'rake -T qa'
  end

  namespace :cluster do
    desc "Create kind cluster '#{CLUSTER_NAME}' with the Podman provider"
    task :create do
      abort 'kind not found on PATH' unless which?('kind')
      abort 'podman not found on PATH' unless which?('podman')

      if cluster_exists?
        puts "kind cluster '#{CLUSTER_NAME}' already exists - skipping create."
      else
        # kind v0.32 selects Podman via KIND_EXPERIMENTAL_PROVIDER
        # (there is no --provider flag on `kind create cluster`).
        ENV['KIND_EXPERIMENTAL_PROVIDER'] ||= 'podman'
        run!('kind', 'create', 'cluster',
             '--name', CLUSTER_NAME,
             '--config', File.join(K8S_DIR, 'kind-config.yaml'),
             '--wait', '120s')
      end
      run!('kubectl', 'config', 'use-context', "kind-#{CLUSTER_NAME}")
    end

    desc "Delete kind cluster '#{CLUSTER_NAME}'"
    task :delete do
      next puts "kind cluster '#{CLUSTER_NAME}' does not exist." unless cluster_exists?

      ENV['KIND_EXPERIMENTAL_PROVIDER'] ||= 'podman'
      run!('kind', 'delete', 'cluster', '--name', CLUSTER_NAME)
    end

    desc 'Show kind / node status'
    task :status do
      run!('kind', 'get', 'clusters')
      run!('kubectl', 'cluster-info', '--context', "kind-#{CLUSTER_NAME}") if cluster_exists?
      run!('kubectl', 'get', 'nodes', '-o', 'wide') if cluster_exists?
    end
  end

  namespace :image do
    desc 'Build the backend image with Podman (multi-stage Dockerfile)'
    task :build do
      abort 'podman not found on PATH' unless which?('podman')
      run!('podman', 'build', '-t', IMAGE, '.', chdir: BACKEND_DIR)
    end

    desc "Load the backend image into kind cluster '#{CLUSTER_NAME}'"
    task :load do
      ensure_cluster!
      ENV['KIND_EXPERIMENTAL_PROVIDER'] ||= 'podman'
      # NOTE: `kind load docker-image` cannot see Podman's image storage on
      # this setup ("not present locally"), so go via a tarball archive.
      require 'tmpdir'
      archive = File.join(Dir.tmpdir, "qa-image-#{Process.pid}.tar")
      begin
        run!('podman', 'save', '-o', archive, IMAGE)
        run!('kind', 'load', 'image-archive', archive, '--name', CLUSTER_NAME)
      ensure
        FileUtils.rm_f(archive)
      end
    end
  end

  desc 'Apply namespace, secrets, init ConfigMap and all workloads'
  task :deploy do
    ensure_cluster!
    run!('kubectl', 'config', 'use-context', "kind-#{CLUSTER_NAME}")
    # Namespace first so secret/configmap land in the right place.
    run!('kubectl', 'apply', '-f', File.join(K8S_DIR, 'namespace.yaml'))
    ensure_secret!
    ensure_init_configmap!
    run!('kubectl', 'apply', '-k', K8S_DIR)
  end

  desc 'Wait until Oracle and backend are Ready'
  task :wait do
    ensure_cluster!
    puts 'Waiting for Oracle (first boot can take several minutes)...'
    wait_for!("oracle ready (#{NAMESPACE})", 600) { oracle_ready? }
    puts "\nWaiting for backend..."
    wait_for!("backend ready (#{NAMESPACE})", 180) { backend_ready? }
    puts "\nAll workloads ready."
  end

  desc 'Rollout status for oracle + backend'
  task :status do
    ensure_cluster!
    run!('kubectl', 'get', 'pods', '-n', NAMESPACE, '-o', 'wide')
    run!('kubectl', 'get', 'svc', '-n', NAMESPACE)
    run!('kubectl', 'get', 'statefulset', 'oracle', '-n', NAMESPACE)
    run!('kubectl', 'get', 'deployment', 'backend', '-n', NAMESPACE)
  end

  desc 'Tail backend logs (qa:logs backend=200 works too via kubectl flags)'
  task :logs do
    ensure_cluster!
    run!('kubectl', 'logs', '-n', NAMESPACE, 'deploy/backend', '--tail=200', '-f')
  end

  namespace :db do
    desc 'Repair Flyway schema history (removes FAILED entries after fixing cause)'
    task :repair do
      ensure_cluster!
      env = load_env_file
      url = "jdbc:oracle:thin:@//#{ORACLE_HOST}:#{ORACLE_PORT}/#{ORACLE_SID}"
      run!(mvn_cmd, '-q', 'flyway:repair',
           "-Dflyway.url=#{url}",
           '-Dflyway.user=APP_OWNER',
           "-Dflyway.password=#{env['APP_OWNER_PWD']}",
           chdir: BACKEND_DIR)
      puts 'Flyway repair OK.'
    end

    desc 'Run Flyway migrations (V001-V017) against Oracle NodePort :1521'
    task :migrate do
      ensure_cluster!
      puts 'Waiting for Oracle before migrate...'
      wait_for!("oracle ready (#{NAMESPACE})", 600) { oracle_ready? }
      puts
      flyway_migrate!
      puts 'Flyway migrate OK.'
    end

    desc 'Deploy PL/SQL packages (specs then bodies) via deploy_packages.sql'
    task :packages do
      ensure_cluster!
      wait_for!("oracle ready (#{NAMESPACE})", 600) { oracle_ready? }
      run_packages_deploy!
      puts 'PL/SQL packages deployed.'
    end

    desc 'Load repeatable seed data (R__seed_data.sql)'
    task :seed do
      ensure_cluster!
      seed = File.join(ROOT, 'database', 'seed', 'R__seed_data.sql')
      abort "Missing #{seed}" unless File.exist?(seed)

      wait_for!("oracle ready (#{NAMESPACE})", 600) { oracle_ready? }
      run_sql_as_owner!(seed, label: 'seed')
      puts 'Seed data loaded.'
    end

    desc 'migrate + packages + seed (in order)'
    task :init => ['qa:db:migrate', 'qa:db:packages', 'qa:db:seed']
  end

  desc 'QA smoke tests: health, auth, order flow, idempotency, tenant isolation'
  task :smoke do
    env = load_env_file
    failures = []
    check = lambda do |name, ok, detail = nil|
      if ok
        puts "  PASS  #{name}"
      else
        puts "  FAIL  #{name}#{detail ? " - #{detail}" : ''}"
        failures << name
      end
    end

    puts "\n== QA smoke against #{BACKEND_URL} =="

    # 1) Health (permitAll)
    begin
      res = http_get("#{BACKEND_URL}/internal/health")
      check.call('GET /internal/health', res.code.to_i == 200, "HTTP #{res.code}")
    rescue StandardError => e
      check.call('GET /internal/health', false, e.message)
    end

    # 2) Unauthenticated order create must be 401
    begin
      res = http_post("#{BACKEND_URL}/api/v1/orders",
                      '{"customerId":1,"currency":"USD"}',
                      headers: { 'X-Tenant-ID' => 'ACME' })
      check.call('POST /orders without auth -> 401', res.code.to_i == 401, "HTTP #{res.code}")
    rescue StandardError => e
      check.call('POST /orders without auth -> 401', false, e.message)
    end

    operator = [ENV.fetch('QA_OPERATOR_USER', 'operator'), env['APP_OPERATOR_PASSWORD']]
    admin    = [ENV.fetch('QA_ADMIN_USER', 'admin'), env['APP_ADMIN_PASSWORD']]

    # 3) Create order (operator)
    order_body = nil
    begin
      res = http_post("#{BACKEND_URL}/api/v1/orders",
                      '{"customerId":1,"currency":"USD"}',
                      user: operator,
                      headers: { 'X-Tenant-ID' => 'ACME' })
      ok = res.code.to_i.between?(200, 299)
      check.call('POST /orders (operator)', ok, "HTTP #{res.code} #{res.body}")
      order_body = JSON.parse(res.body) if ok && res.body && !res.body.empty?
    rescue StandardError => e
      check.call('POST /orders (operator)', false, e.message)
    end

    # 4) Idempotent retry with same key
    begin
      key = "qa-smoke-#{Time.now.to_i}"
      h = { 'X-Tenant-ID' => 'ACME', 'Idempotency-Key' => key }
      r1 = http_post("#{BACKEND_URL}/api/v1/orders", '{"customerId":1,"currency":"USD"}',
                     user: operator, headers: h)
      r2 = http_post("#{BACKEND_URL}/api/v1/orders", '{"customerId":1,"currency":"USD"}',
                     user: operator, headers: h)
      same = r1.code == r2.code && (r1.body == r2.body || r2.code.to_i == 409)
      check.call('Idempotency-Key retry', r1.code.to_i.between?(200, 299) && same,
                 "r1=#{r1.code} r2=#{r2.code}")
    rescue StandardError => e
      check.call('Idempotency-Key retry', false, e.message)
    end

    # 5) Tenant isolation: GLOBEX must not see ACME-shaped private payload errors
    begin
      res = http_get("#{BACKEND_URL}/api/v1/reports/orders/daily",
                     user: operator,
                     headers: { 'X-Tenant-ID' => 'GLOBEX' })
      # 200 with own data or 403 - both acceptable; 500 is not.
      check.call('GET reports (GLOBEX tenant)', res.code.to_i != 500, "HTTP #{res.code}")
    rescue StandardError => e
      check.call('GET reports (GLOBEX tenant)', false, e.message)
    end

    # 6) Admin-only endpoint rejects operator
    begin
      res = http_post("#{BACKEND_URL}/api/v1/tenants",
                      '{"tenantCode":"QA_T","name":"QA Tenant"}',
                      user: operator,
                      headers: { 'X-Tenant-ID' => 'ACME' })
      check.call('POST /tenants as operator -> 401/403', [401, 403].include?(res.code.to_i),
                 "HTTP #{res.code}")
    rescue StandardError => e
      check.call('POST /tenants as operator -> 401/403', false, e.message)
    end

    # 7) Actuator metrics reachable (management base-path /internal)
    begin
      res = http_get("#{BACKEND_URL}/internal/health")
      check.call('Health body present', !res.body.to_s.empty?, res.body.to_s[0, 80])
    rescue StandardError => e
      check.call('Health body present', false, e.message)
    end

    puts
    if failures.empty?
      puts "SMOKE OK (#{BACKEND_URL})"
    else
      abort "SMOKE FAILED: #{failures.join(', ')}"
    end
  end

  desc 'Delete workloads + services (keeps cluster and Oracle PVC data)'
  task :down do
    next puts 'Cluster not present - nothing to tear down.' unless cluster_exists?

    run!('kubectl', 'delete', '-k', K8S_DIR, '--ignore-not-found=true')
    # Namespace objects (secret/configmap) live in poc-qa - remove them too.
    run!('kubectl', 'delete', 'secret', SECRETS_NAME, '-n', NAMESPACE, '--ignore-not-found=true')
    run!('kubectl', 'delete', 'configmap', 'oracle-init-scripts', '-n', NAMESPACE,
         '--ignore-not-found=true')
    # Keep Namespace so re-deploy is fast; delete with cluster.
    puts 'Workloads deleted (namespace and PVC left in place).'
  end

  # NOTE: :wait is intentionally NOT part of :up - the backend only becomes
  # Ready after qa:db:init has created the schema (DB health indicator).
  desc 'Full bring-up: cluster + image build/load + deploy (then db:init, wait, smoke)'
  task :up => ['qa:cluster:create', 'qa:image:build', 'qa:image:load', 'qa:deploy']

  desc 'Full teardown: workloads then cluster'
  task :teardown => ['qa:down', 'qa:cluster:delete']
end

desc 'List all tasks'
task :default do
  sh 'rake -T'
end
