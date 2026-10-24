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
require 'timeout'

ROOT        = __dir__
K8S_DIR     = File.join(ROOT, 'infrastructure', 'k8s')
BACKEND_DIR = File.join(ROOT, 'backend')
ENV_FILE    = File.join(ROOT, 'infrastructure', 'podman', '.env')
INIT_SQL    = File.join(ROOT, 'infrastructure', 'podman', 'scripts', '01-init-db.sql')

CLUSTER_NAME = ENV.fetch('QA_CLUSTER', 'poc-oracle')
NAMESPACE    = ENV.fetch('QA_NAMESPACE', 'poc-oracle')
# NOTE: keep the localhost/ prefix - it is the exact name podman stores
# and kind/containerd looks up (imagePullPolicy: Never needs exact match).
IMAGE        = ENV.fetch('QA_IMAGE', 'localhost/enterprise-orders-qa:0.0.1')
BACKEND_URL  = ENV.fetch('QA_BACKEND_URL', 'http://localhost:8080')
ORACLE_HOST  = ENV.fetch('QA_ORACLE_HOST', 'localhost')
ORACLE_PORT  = ENV.fetch('QA_ORACLE_PORT', '1521')
ORACLE_SID   = ENV.fetch('QA_ORACLE_SERVICE', 'pdb_oltp')
# Both application PDBs get the SAME Flyway schema, packages and seed, so
# tenant_id values line up across them: ORACLE_SID (PDB_OLTP) is the shared
# VPD PDB; DEDICATED_SID (PDB_CORP) is the dedicated PDB that serves
# ENTERPRISE tenants. Everything that defines the schema runs on both.
DEDICATED_SID      = ENV.fetch('QA_ORACLE_DEDICATED_SERVICE', 'pdb_corp')
PROVISION_SERVICES = [ORACLE_SID, DEDICATED_SID].uniq.freeze
SECRETS_NAME = 'poc-oracle-secrets'

# LAB-ONLY defaults (documented demo credentials, never real secrets).
# Fallback used only when infrastructure/podman/.env is absent (a warn
# is printed). Keep in sync with .env.example AND with the DEFINE
# defaults in scripts/01-init-db.sql (first boot must create the users
# with these same values).
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

# Strip credentials from anything we echo (console `$ ...` lines and
# error messages): SQL*Plus connect strings `user/pass@host` become
# `user/****@host` and `-Dflyway.password=...` becomes `****`.
# NOTE: this only protects LOGS - the real argv still carries the secret
# for the flyway password (Flyway only reads it as a system property).
def redact(text)
  text.to_s
      .gsub(/([A-Za-z0-9_$.]+)\/([^\/\s@]+)@/, '\1/****@')
      .gsub(/-Dflyway\.password=\S*/, '-Dflyway.password=****')
end

def run!(*args, chdir: nil)
  cmd = args.length == 1 ? args.first : args
  shown = redact(cmd.is_a?(Array) ? cmd.join(' ') : cmd)
  puts "\n$ #{shown}"
  argv = cmd.is_a?(Array) ? cmd : [cmd]
  # NOTE: no kwargs splat - system(str, **{}) raises ArgumentError on Ruby/Windows.
  success = if chdir
              Dir.chdir(chdir) { system(*argv) }
            else
              system(*argv)
            end
  return if success

  status = $CHILD_STATUS
  abort "Command failed (#{status.nil? ? '?' : status.exitstatus}): #{shown}"
end

def capture!(*args, chdir: nil)
  cmd = args.length == 1 ? args.first : args
  shown = redact(cmd.is_a?(Array) ? cmd.join(' ') : cmd)
  puts "\n$ #{shown}"
  opts = {}
  opts[:chdir] = chdir if chdir
  out = IO.popen(cmd, err: [:child, :out], **opts, &:read)
  raise "Command failed: #{shown}\n#{out}" unless $CHILD_STATUS.success?

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

# Create/refresh the QA Secret without putting any value on a command
# line (`kubectl create secret --from-literal=k=v` is visible in `ps`
# and in our own `$ ...` echo). Values go into a 0600 Tempfile consumed
# via --from-env-file; the file is always unlinked in ensure.
def ensure_secret!
  require 'tempfile'
  env = load_env_file
  data = {
    'ORACLE_PASSWORD'       => env['ORACLE_PASSWORD'],
    'APP_OWNER_PWD'         => env['APP_OWNER_PWD'],
    'APP_RUNTIME_PWD'       => env['APP_RUNTIME_PWD'],
    'APP_REPORTING_PWD'     => env['APP_REPORTING_PWD'],
    'APP_ADMIN_PASSWORD'    => env['APP_ADMIN_PASSWORD'],
    'APP_OPERATOR_PASSWORD' => env['APP_OPERATOR_PASSWORD']
  }
  file = Tempfile.new(['poc-oracle-secrets', '.env'])
  begin
    file.chmod(0o600)
    data.each { |k, v| file.write("#{k}=#{v}\n") }
    file.flush
    file.close
    args = ['create', 'secret', 'generic', SECRETS_NAME, '-n', NAMESPACE,
            "--from-env-file=#{file.path}", '--dry-run=client', '-o', 'yaml']
    yaml = capture!('kubectl', *args)
    kubectl('apply', '-f', '-', input: yaml)
  ensure
    file.close unless file.closed?
    file.unlink
  end
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

# Flyway password for -Dflyway.password: ENV['FLYWAY_PASSWORD'] first
# (kept out of the .env file and never printed), lab .env/DEFAULTS as
# fallback so existing bring-up keeps working. The value is still part
# of the mvn argv (Flyway only accepts it as a system property), so it
# is redacted from our own output but remains visible in `ps` for the
# duration of the mvn run - documented residual in the QA report.
def flyway_password
  pwd = ENV['FLYWAY_PASSWORD'].to_s
  pwd = load_env_file['APP_OWNER_PWD'].to_s if pwd.empty?
  abort 'No Flyway password: set FLYWAY_PASSWORD or APP_OWNER_PWD (infrastructure/podman/.env).' if pwd.empty?

  pwd
end

def flyway_run!(goal, service: ORACLE_SID)
  url = "jdbc:oracle:thin:@//#{ORACLE_HOST}:#{ORACLE_PORT}/#{service}"
  run!(mvn_cmd, '-q', "flyway:#{goal}",
       "-Dflyway.url=#{url}",
       '-Dflyway.user=APP_OWNER',
       "-Dflyway.password=#{flyway_password}",
       chdir: BACKEND_DIR)
end

def sqlplus_available?
  which?('sqlplus')
end

# Pod name of the Oracle StatefulSet (single replica).
def oracle_pod!
  pod = capture!('kubectl', 'get', 'pods', '-n', NAMESPACE, '-l', 'app=oracle',
                 '-o', 'jsonpath={.items[0].metadata.name}').strip
  abort 'Oracle pod not found' if pod.empty?

  pod
end

# Copy a local file into the Oracle pod (relative source avoids the
# Windows drive-letter misparsing of `kubectl cp`).
def cp_to_pod!(local_path, remote_path)
  run!('kubectl', 'cp', File.basename(local_path),
       "#{NAMESPACE}/#{oracle_pod!}:#{remote_path}",
       chdir: File.dirname(local_path))
end

# Pipe SQL text through the in-pod sqlplus, print output, fail loudly on
# error exit codes AND on ORA-/SP2-/PLS- markers (sqlplus exit codes alone
# are unreliable - a failed script can still exit 0). Returns stdout.
# `service:` selects the connect service (CDB root uses FREE); `sysdba:`
# appends the SYSDBA clause (SYS-only connections).
def sql_via_pod!(stdin_text, user, pass, label, service: ORACLE_SID, sysdba: false)
  conn = "#{user}/#{pass}@//localhost:1521/#{service}"
  conn += ' as sysdba' if sysdba
  cmd = ['kubectl', 'exec', '-i', '-n', NAMESPACE, oracle_pod!,
         '--', 'bash', '-lc', "sqlplus -L -S #{Shellwords.escape(conn)}"]
  puts "\n$ #{redact(cmd.join(' '))}  # via in-pod sqlplus (#{label})"
  out = IO.popen(cmd, 'r+') do |io|
    io.write(stdin_text)
    io.close_write
    io.read
  end
  puts out unless out.strip.empty?
  raise "SQL failed (#{label})" unless $CHILD_STATUS.success?

  bad = out.lines.grep(/ORA-\d{5}|SP2-\d{4}|PLS-\d{5}/)
  raise "SQL failed (#{label}): #{bad.first.strip}" unless bad.empty?

  out
end

# Run a script already copied into the pod with positional &1..&n args.
def exec_sql_file_in_pod!(remote_script, script_args, user, pass, label)
  args = script_args.map(&:to_s).join(' ')
  sql_via_pod!("WHENEVER SQLERROR EXIT SQL.SQLCODE\n@#{remote_script} #{args}\nEXIT SUCCESS\n",
               user, pass, label)
end

# Run a self-verifying SQL suite already staged in the pod.
# Unlike sql_via_pod!, this does NOT scan for ORA-/SP2-/PLS- markers:
# suites like T_security deliberately print EXPECTED ORA errors
# ("rejected as expected"). The contract is: exit status 0 AND the
# suite's own PASSED marker present in the output.
def run_sql_suite_in_pod!(remote_script, script_args, user, pass, label, pass_marker)
  args = script_args.map(&:to_s).join(' ')
  conn = "#{user}/#{pass}@//localhost:1521/#{ORACLE_SID}"
  cmd = ['kubectl', 'exec', '-i', '-n', NAMESPACE, oracle_pod!,
         '--', 'bash', '-lc', "sqlplus -L -S #{Shellwords.escape(conn)}"]
  stdin_text = "WHENEVER SQLERROR EXIT FAILURE\n@#{remote_script} #{args}\nEXIT SUCCESS\n"
  puts "\n$ #{redact(cmd.join(' '))}  # via in-pod sqlplus (#{label})"
  out = IO.popen(cmd, 'r+') do |io|
    io.write(stdin_text)
    io.close_write
    io.read
  end
  puts out unless out.strip.empty?
  raise "SQL suite failed (#{label}): non-zero exit" unless $CHILD_STATUS.success?
  raise "SQL suite failed (#{label}): missing '#{pass_marker}'" unless out.include?(pass_marker)

  puts "SUITE PASSED (#{label})"
  out
end

# Run a SQL script as APP_OWNER against the Oracle NodePort using sqlplus.
# Falls back to an in-pod sqlplus via kubectl exec when no local client exists.
# Pass db_user:/db_password: to run as another schema user (e.g. APP_RUNTIME).
def run_sql_as_owner!(script_path_or_stdin, label:, db_user: nil, db_password: nil, service: ORACLE_SID)
  env = load_env_file
  user = db_user || env['APP_OWNER_USER']
  pass = db_password || env['APP_OWNER_PWD']
  connect = "#{user}/#{pass}@//#{ORACLE_HOST}:#{ORACLE_PORT}/#{service}"

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
      out
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
      out
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
    # gvenzl image exposes sqlplus for OS-auth / app users (no NodePort needed).
    sql_via_pod!(wrapper, user, pass, label, service: service)
  end
end

def run_packages_deploy!(service: ORACLE_SID)
  env = load_env_file
  user = env['APP_OWNER_USER']
  pass = env['APP_OWNER_PWD']
  packages_dir = File.join(ROOT, 'database', 'packages')
  deploy = File.join(packages_dir, 'deploy_packages.sql')
  abort "Missing #{deploy}" unless File.exist?(deploy)

  # deploy_packages.sql uses @@ relative includes - run from its directory.
  if sqlplus_available?
    run!('sqlplus', '-L', '-S', "#{user}/#{pass}@//#{ORACLE_HOST}:#{ORACLE_PORT}/#{service}",
         "@#{deploy}", chdir: packages_dir)
  else
    conn = "#{user}/#{pass}@//localhost:1521/#{service}"
    # NOTE: kubectl cp needs a POD name (statefulset/x is not accepted).
    pod = oracle_pod!

    # Copy package sources into the pod, then run deploy from that dir.
    run!('kubectl', 'exec', '-n', NAMESPACE, pod, '--', 'mkdir', '-p', '/tmp/packages')
    Dir.children(packages_dir).select { |f| f.end_with?('.sql') }.each do |f|
      cp_to_pod!(File.join(packages_dir, f), "/tmp/packages/#{f}")
    end
    # Keep the remote bash command on one line; a multiline Ruby string can
    # carry Windows CRLF through kubectl exec into `bash -lc`.
    script = ['set -euo pipefail', 'cd /tmp/packages',
              "sqlplus -L -S #{Shellwords.escape(conn)} @deploy_packages.sql"].join(' && ')
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
    # NOTE: rollout status, not just readyReplicas - during a rolling
    # update the OLD pod stays Ready and replica counts lie, routing
    # smoke tests to stale code.
    puts "\nWaiting for backend rollout..."
    run!('kubectl', 'rollout', 'status', 'deploy/backend', '-n', NAMESPACE, '--timeout=300s')
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
      PROVISION_SERVICES.each do |service|
        flyway_run!(:repair, service: service)
        puts "Flyway repair OK (#{service})."
      end
    end

    desc 'Run Flyway migrations (V001-V026) on every provisioned PDB (shared + dedicated)'
    task :migrate do
      ensure_cluster!
      puts 'Waiting for Oracle before migrate...'
      wait_for!("oracle ready (#{NAMESPACE})", 600) { oracle_ready? }
      puts
      PROVISION_SERVICES.each do |service|
        flyway_run!(:migrate, service: service)
        puts "Flyway migrate OK (#{service})."
      end
    end

    desc 'Deploy PL/SQL packages (specs then bodies) on every provisioned PDB'
    task :packages do
      ensure_cluster!
      wait_for!("oracle ready (#{NAMESPACE})", 600) { oracle_ready? }
      PROVISION_SERVICES.each { |service| run_packages_deploy!(service: service) }
      puts 'PL/SQL packages deployed.'
    end

    desc 'Load repeatable seed data (R__seed_data.sql) on every provisioned PDB'
    task :seed do
      ensure_cluster!
      seed = File.join(ROOT, 'database', 'seed', 'R__seed_data.sql')
      abort "Missing #{seed}" unless File.exist?(seed)

      wait_for!("oracle ready (#{NAMESPACE})", 600) { oracle_ready? }
      PROVISION_SERVICES.each do |service|
        run_sql_as_owner!(seed, label: "seed[#{service}]", service: service)
      end
      puts 'Seed data loaded.'
    end

    desc 'migrate + packages + seed (in order)'
    task :init => ['qa:db:migrate', 'qa:db:packages', 'qa:db:seed']

    desc 'Concurrency FT-003: WORKERS racers on stock=1, exactly 1 CONFIRMED (QA_WORKERS, default 10)'
    task :concurrency do
      ensure_cluster!
      env = load_env_file
      workers = (ENV['QA_WORKERS'] || 10).to_i
      ouser = env['APP_OWNER_USER']
      opass = env['APP_OWNER_PWD']
      ruser = env['APP_RUNTIME_USER']
      rpass = env['APP_RUNTIME_PWD']
      wait_for!("oracle ready (#{NAMESPACE})", 600) { oracle_ready? }

      conc_dir = File.join(ROOT, 'tests', 'concurrency')
      %w[C_setup_stock.sql C_concurrent_orders_worker.sql C_assert_results.sql].each do |f|
        cp_to_pod!(File.join(conc_dir, f), "/tmp/#{f}")
      end

      # Seed IDs by business code (identity columns are not deterministic).
      lookup = <<~SQL
        SET SERVEROUTPUT ON HEADING OFF FEEDBACK OFF PAGESIZE 0
        ALTER SESSION SET CURRENT_SCHEMA = APP_OWNER;
        DECLARE
          v_t NUMBER; v_c NUMBER; v_p NUMBER; v_w NUMBER;
        BEGIN
          SELECT tenant_id INTO v_t FROM tenant WHERE tenant_code='ACME';
          pkg_tenant.set_tenant_context(v_t);
          SELECT customer_id INTO v_c FROM customer WHERE tenant_id=v_t AND customer_number='C0001';
          SELECT product_id INTO v_p FROM product WHERE tenant_id=v_t AND sku='PRD-001';
          SELECT warehouse_id INTO v_w FROM warehouse WHERE tenant_id=v_t AND code='WH1';
          DBMS_OUTPUT.PUT_LINE('IDS:'||v_t||':'||v_c||':'||v_p||':'||v_w);
        END;
        /
      SQL
      out = sql_via_pod!("WHENEVER SQLERROR EXIT SQL.SQLCODE\n#{lookup}\nEXIT SUCCESS\n",
                         ouser, opass, 'resolve-ids')
      ids = out[/IDS:(\d+:\d+:\d+:\d+)/, 1]
      abort 'Could not resolve ACME seed IDs' unless ids

      tenant_id, customer_id, product_id, warehouse_id = ids.split(':')
      puts "Seed IDs: tenant=#{tenant_id} customer=#{customer_id} " \
           "product=#{product_id} warehouse=#{warehouse_id}"
      tbl = "concurrency_results_#{Time.now.to_i}"
      begin
        sql_via_pod!(<<~SQL, ouser, opass, 'results-table')
          WHENEVER SQLERROR EXIT SQL.SQLCODE
          CREATE TABLE #{tbl} (worker_id NUMBER, order_id NUMBER, result VARCHAR2(30), error_msg VARCHAR2(4000), ts TIMESTAMP);
          GRANT SELECT, INSERT ON #{tbl} TO #{ruser};
          EXIT SUCCESS
        SQL
        # Closure fix (V023): stock setup runs as APP_OWNER - APP_RUNTIME
        # no longer holds EXECUTE on pkg_inventory (set_test_stock).
        exec_sql_file_in_pod!('/tmp/C_setup_stock.sql',
                              [tenant_id, product_id, warehouse_id, 1],
                              ouser, opass, 'setup-stock')

        puts "Launching #{workers} workers..."
        results = Array.new(workers)
        threads = (1..workers).map do |wid|
          Thread.new do
            begin
              o = exec_sql_file_in_pod!('/tmp/C_concurrent_orders_worker.sql',
                                        [wid, tenant_id, customer_id, product_id, warehouse_id, tbl],
                                        ruser, rpass, "worker-#{wid}")
              results[wid - 1] = { ok: true, out: o }
            rescue StandardError => e
              results[wid - 1] = { ok: false, out: e.message }
            end
          end
        end
        Timeout.timeout(600) { threads.each(&:join) }
        bad = results.each_index.select { |i| !results[i] || !results[i][:ok] }
        unless bad.empty?
          bad.each { |i| puts "worker #{i + 1} exited non-zero: #{results[i] ? results[i][:out] : 'no result'}" }
          puts 'Continuing to count-check + assert (a crashed worker leaves no row).'
        end
        puts 'All workers finished.'

        # Every worker must have recorded exactly one row (ERROR rows count:
        # C_assert_results judges business outcome, this judges completeness).
        cnt_out = sql_via_pod!(<<~SQL, ouser, opass, 'count-workers')
          WHENEVER SQLERROR EXIT SQL.SQLCODE
          SET HEADING OFF FEEDBACK OFF PAGESIZE 0
          SELECT COUNT(*) FROM #{tbl};
          EXIT SUCCESS
        SQL
        recorded = cnt_out.lines.map(&:strip).select { |l| l =~ /^\d+$/ }.last.to_i
        puts "Workers recorded: #{recorded}/#{workers}"
        abort "Concurrency incomplete: #{recorded}/#{workers} workers recorded" unless recorded == workers

        exec_sql_file_in_pod!('/tmp/C_assert_results.sql', [tbl], ouser, opass, 'assert')
        puts 'CONCURRENCY ASSERTIONS PASSED.'
      ensure
        # Always restore stock (seed MERGEs never replenish) + drop results.
        if defined?(tenant_id) && tenant_id && defined?(tbl) && tbl
          begin
            exec_sql_file_in_pod!('/tmp/C_setup_stock.sql',
                                  [tenant_id, product_id, warehouse_id, 100],
                                  ouser, opass, 'restore-stock')
          rescue StandardError => e
            warn "WARNING: stock restore failed: #{e.message}"
          end
          begin
            sql_via_pod!("DROP TABLE #{tbl} PURGE;\nEXIT SUCCESS\n", ouser, opass, 'cleanup-table')
          rescue StandardError => e
            warn "WARNING: results table cleanup failed: #{e.message}"
          end
        end
      end
    end

    desc 'Verify the 16 VPD policies are present with correct attributes (APP_OWNER)'
    task :'verify-vpd' do
      ensure_cluster!
      env = load_env_file
      wait_for!("oracle ready (#{NAMESPACE})", 600) { oracle_ready? }
      script = File.join(ROOT, 'database', 'security', 'verify_vpd_policies.sql')
      abort "Missing #{script}" unless File.exist?(script)
      cp_to_pod!(script, '/tmp/verify_vpd_policies.sql')
      run_sql_suite_in_pod!('/tmp/verify_vpd_policies.sql', [],
                            env['APP_OWNER_USER'], env['APP_OWNER_PWD'],
                            'verify-vpd',
                            '=== VPD verification PASSED: 16/16 policies OK ===')
    end

    desc 'VPD cross-tenant isolation suite T_security_tenant_isolation.sql (APP_RUNTIME)'
    task :security do
      ensure_cluster!
      env = load_env_file
      wait_for!("oracle ready (#{NAMESPACE})", 600) { oracle_ready? }
      script = File.join(ROOT, 'tests', 'security', 'T_security_tenant_isolation.sql')
      abort "Missing #{script}" unless File.exist?(script)
      cp_to_pod!(script, '/tmp/T_security_tenant_isolation.sql')
      run_sql_suite_in_pod!('/tmp/T_security_tenant_isolation.sql', [],
                            env['APP_RUNTIME_USER'], env['APP_RUNTIME_PWD'],
                            'security', '=== SECURITY: tenant isolation PASSED ===')
    end

    desc 'Order-to-payment integration suite IT_order_to_payment.sql (APP_RUNTIME)'
    task :it do
      ensure_cluster!
      env = load_env_file
      wait_for!("oracle ready (#{NAMESPACE})", 600) { oracle_ready? }
      script = File.join(ROOT, 'tests', 'integration', 'IT_order_to_payment.sql')
      abort "Missing #{script}" unless File.exist?(script)
      cp_to_pod!(script, '/tmp/IT_order_to_payment.sql')
      out = run_sql_suite_in_pod!('/tmp/IT_order_to_payment.sql', [],
                                  env['APP_RUNTIME_USER'], env['APP_RUNTIME_PWD'],
                                  'it', '=== IT: order-to-payment flow PASSED ===')
      # Owner-side cleanup: the suite's own cleanup is best-effort by design
      # (APP_RUNTIME holds no DELETE on business tables - least privilege),
      # so a passing run still leaves its CONFIRMED+PAID probe order behind.
      # Remove exactly this run's rows as APP_OWNER (children before parent)
      # and release its 2 reserved units (qty=2 PRD-001/WH1/ACME, fixed in
      # the suite). Skipped when the suite cleaned up after itself.
      if out.include?('Cleanup OK: test order rows removed')
        puts 'Suite self-cleanup OK - nothing left behind.'
      else
        oid = out[/Created order_id=(\d+)/, 1]
        abort 'IT passed but order_id not found - manual cleanup required' unless oid
        sql_via_pod!(<<~SQL, env['APP_OWNER_USER'], env['APP_OWNER_PWD'], 'it-owner-cleanup')
          WHENEVER SQLERROR EXIT SQL.SQLCODE
          SET SERVEROUTPUT ON
          VARIABLE v_t NUMBER
          BEGIN SELECT tenant_id INTO :v_t FROM tenant WHERE tenant_code = 'ACME'; END;
          /
          DELETE FROM payment WHERE tenant_id = :v_t AND order_id = #{oid};
          DELETE FROM order_status_history WHERE tenant_id = :v_t AND order_id = #{oid};
          DELETE FROM sales_order_line WHERE tenant_id = :v_t AND order_id = #{oid};
          DELETE FROM outbox_event WHERE tenant_id = :v_t AND aggregate_type = 'SALES_ORDER'
            AND aggregate_id = '#{oid}';
          DELETE FROM audit_event WHERE tenant_id = :v_t AND entity_type = 'SALES_ORDER'
            AND entity_id = '#{oid}';
          DELETE FROM sales_order WHERE tenant_id = :v_t AND order_id = #{oid};
          UPDATE inventory
             SET available_quantity = available_quantity + 2,
                 reserved_quantity = reserved_quantity - 2
           WHERE tenant_id = :v_t
             AND product_id = (SELECT product_id FROM product WHERE tenant_id = :v_t AND sku = 'PRD-001')
             AND warehouse_id = (SELECT warehouse_id FROM warehouse WHERE tenant_id = :v_t AND code = 'WH1');
          COMMIT;
          PROMPT IT owner-cleanup OK
          EXIT SUCCESS
        SQL
      end
    end

    desc 'verify-vpd + security + it (full DB verification)'
    task :verify => ['qa:db:verify-vpd', 'qa:db:security', 'qa:db:it'] do
      puts 'DB VERIFICATION PASSED (verify-vpd + security + it).'
    end

    namespace :rman do
      desc 'Restore CDB + PDB_OLTP from RMAN pieces (requires ORACLE_SID + rman on PATH, unexecuted in lab)'
      task :restore do
        sid = ENV['ORACLE_SID'].to_s
        abort 'ERROR: ORACLE_SID is not set.' if sid.empty?
        abort 'ERROR: rman not found on PATH.' unless system('where rman >NUL 2>&1')
        rcv = File.join(ROOT, 'database', 'backup', 'rman_restore_body.rcv')
        abort "ERROR: #{rcv} missing." unless File.exist?(rcv)
        puts "Pre-checks OK. SID=#{sid}. Starting restore..."
        run!('rman', 'target', '/', "@#{rcv}")
        puts 'RESTORE DONE. Open PDB_OLTP and verify with database/backup/backup_verify.sql.'
      end
    end
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
    order_id = nil
    begin
      res = http_post("#{BACKEND_URL}/api/v1/orders",
                      '{"customerId":1,"currency":"USD"}',
                      user: operator,
                      headers: { 'X-Tenant-ID' => 'ACME' })
      ok = res.code.to_i.between?(200, 299)
      check.call('POST /orders (operator)', ok, "HTTP #{res.code} #{res.body}")
      order_id = JSON.parse(res.body)['orderId'] if ok && res.body && !res.body.empty?
    rescue StandardError => e
      check.call('POST /orders (operator)', false, e.message)
    end

    # 4) Idempotency-Key is honored by create/confirm/cancel/payment
    # (FR-014); this step demonstrates it on confirm. Make the order
    # confirmable (add a seed line), confirm it
    # with key K (2xx leaves a COMPLETED record), then reuse K for a
    # DIFFERENT order -> 409 IDEMPOTENCY_KEY_CONFLICT. Note: a FAILED
    # confirm rolls back (no record left), so r1 must succeed.
    begin
      if order_id.nil?
        check.call('Idempotency-Key conflict -> 409', false, 'no order created in step 3')
      else
        # Seed-first IDs (same assumption as customerId=1 in step 3):
        # PRD-001 -> product 1, WH1 -> warehouse 1, stock 100 each.
        rl = http_post("#{BACKEND_URL}/api/v1/orders/#{order_id}/lines",
                       '{"productId":1,"quantity":1,"warehouseId":1}',
                       user: operator,
                       headers: { 'X-Tenant-ID' => 'ACME' })
        if !rl.code.to_i.between?(200, 299)
          check.call('Idempotency-Key conflict -> 409', false, "addLine HTTP #{rl.code} #{rl.body}")
        else
          key = "qa-smoke-#{Time.now.to_i}"
          h = { 'X-Tenant-ID' => 'ACME', 'Idempotency-Key' => key }
          r1 = http_post("#{BACKEND_URL}/api/v1/orders/#{order_id}/confirm", '{}',
                         user: operator, headers: h)
          r2 = http_post("#{BACKEND_URL}/api/v1/orders/#{order_id + 10_000}/confirm", '{}',
                         user: operator, headers: h)
          check.call('Idempotency-Key conflict -> 409',
                     r1.code.to_i.between?(200, 299) && r2.code.to_i == 409,
                     "r1=#{r1.code} r2=#{r2.code} #{r2.body}")
        end
      end
    rescue StandardError => e
      check.call('Idempotency-Key conflict -> 409', false, e.message)
    end

    # 5) MV refresh (ADMIN only) - exercises refresh_reporting_views (V018).
    #    Runs BEFORE the isolation check below: the reporting MVs are
    #    REFRESH COMPLETE ON DEMAND, so without a refresh both tenants
    #    would legitimately return [] and any isolation assertion on the
    #    payload would be vacuous on a fresh cluster.
    begin
      res = http_post("#{BACKEND_URL}/api/v1/reports/refresh", '{}',
                      user: admin,
                      headers: { 'X-Tenant-ID' => 'ACME' })
      check.call('POST /reports/refresh (admin)', res.code.to_i.between?(200, 299),
                 "HTTP #{res.code} #{res.body}")
    rescue StandardError => e
      check.call('POST /reports/refresh (admin)', false, e.message)
    end

    # 6) Tenant isolation - real cross-tenant assertions (two probes):
    #    a) the ACME order created in step 3 MUST be readable as ACME
    #       (200) and MUST NOT be readable as GLOBEX (404 = "no such
    #       order for this tenant"; 200 would be a leak);
    #    b) GET /reports/orders/daily twice, ACME vs GLOBEX: both 200,
    #    part A (VPD row isolation, C4-binding bypassed with ADMIN who may
    #    access any tenant): ADMIN+GLOBEX reading an ACME order -> 404;
    #    ADMIN daily reports for ACME vs GLOBEX -> 200/200 with disjoint
    #    single-tenant row sets and different bodies.
    #    part B (C4 user->tenant binding): operator+GLOBEX -> 403
    #    TENANT_NOT_ALLOWED on both order read and reports.
    #    The old check only asserted "not 500", so a leak answering 200
    #    with the other tenant's rows passed. Kept as ONE check to keep
    #    the documented "8 API checks" contract; the detail string lists
    #    every failed sub-condition.
    begin
      problems = []
      if order_id
        own   = http_get("#{BACKEND_URL}/api/v1/orders/#{order_id}",
                         user: operator, headers: { 'X-Tenant-ID' => 'ACME' })
        cross = http_get("#{BACKEND_URL}/api/v1/orders/#{order_id}",
                         user: admin, headers: { 'X-Tenant-ID' => 'GLOBEX' })
        bound = http_get("#{BACKEND_URL}/api/v1/orders/#{order_id}",
                         user: operator, headers: { 'X-Tenant-ID' => 'GLOBEX' })
        problems << "ACME GET own order HTTP #{own.code} (want 200)" unless own.code.to_i == 200
        unless cross.code.to_i == 404
          problems << "ADMIN GLOBEX GET ACME order HTTP #{cross.code} (want 404 - cross-tenant leak?)"
        end
        unless bound.code.to_i == 403 && bound.body.to_s.include?('TENANT_NOT_ALLOWED')
          problems << "operator GLOBEX GET ACME order HTTP #{bound.code} (want 403 TENANT_NOT_ALLOWED) #{bound.body.to_s[0, 80]}"
        end
      else
        problems << 'order probe skipped (no order created in step 3)'
      end

      fetch_daily = lambda do |user, tenant|
        http_get("#{BACKEND_URL}/api/v1/reports/orders/daily",
                 user: user,
                 headers: { 'X-Tenant-ID' => tenant })
      end
      r_acme   = fetch_daily.call(admin, 'ACME')
      r_globex = fetch_daily.call(admin, 'GLOBEX')
      r_bound  = fetch_daily.call(operator, 'GLOBEX')
      if r_acme.code.to_i == 200 && r_globex.code.to_i == 200
        tenant_ids = lambda do |res|
          JSON.parse(res.body).map { |row| row['tenantId'] }.compact.uniq
        end
        acme_ids   = tenant_ids.call(r_acme)
        globex_ids = tenant_ids.call(r_globex)
        unless acme_ids.size == 1 && globex_ids.size == 1 && acme_ids != globex_ids
          problems << "report rows not tenant-scoped (ACME tenantIds=#{acme_ids.inspect}, " \
                      "GLOBEX tenantIds=#{globex_ids.inspect})"
        end
        if r_acme.body.empty? || r_globex.body.empty? || r_acme.body == r_globex.body
          problems << "report bodies empty or identical (seed/refresh must give both tenants rows): " \
                      "ACME=#{r_acme.body[0, 120].inspect} GLOBEX=#{r_globex.body[0, 120].inspect}"
        end
      else
        problems << "GET reports HTTP ACME=#{r_acme.code} GLOBEX=#{r_globex.code} (want 200/200)"
      end
      unless r_bound.code.to_i == 403 && r_bound.body.to_s.include?('TENANT_NOT_ALLOWED')
        problems << "operator GLOBEX GET reports HTTP #{r_bound.code} (want 403 TENANT_NOT_ALLOWED)"
      end

      check.call('Tenant isolation (ACME vs GLOBEX)', problems.empty?,
                 problems.empty? ? nil : problems.join('; '))
    rescue StandardError => e
      check.call('Tenant isolation (ACME vs GLOBEX)', false, e.message)
    end

    # 7) Tenant provisioning RBAC + FR-001 positive path (admin creates,
    #    operator is rejected). The positive body must be fully valid: the
    #    create path runs with a cleared VPD context and writes the audit
    #    row via definer's-rights pkg_audit (APP_OWNER holds EXEMPT ACCESS
    #    POLICY), so 201 proves the whole chain end to end. Fixed code
    #    QA_SMOKE: first run -> 201, re-runs -> 409 TENANT_ALREADY_EXISTS
    #    (procedure reached = path alive, no tenant-table pollution).
    begin
      denied = http_post("#{BACKEND_URL}/api/v1/tenants",
                         '{"tenantCode":"QA_T","name":"QA Tenant"}',
                         user: operator,
                         headers: { 'X-Tenant-ID' => 'ACME' })
      created = http_post("#{BACKEND_URL}/api/v1/tenants",
                          '{"tenantCode":"QA_SMOKE","name":"QA Smoke Tenant",' \
                          '"tier":"STANDARD","isolationMode":"SHARED_SCHEMA"}',
                          user: admin,
                          headers: { 'X-Tenant-ID' => 'ACME' })
      ok_deny = [401, 403].include?(denied.code.to_i)
      ok_create = [201, 409].include?(created.code.to_i)
      check.call('POST /tenants RBAC + FR-001 positive path',
                 ok_deny && ok_create,
                 "operator=#{denied.code} admin=#{created.code} #{created.body.to_s[0, 100]}")
    rescue StandardError => e
      check.call('POST /tenants RBAC + FR-001 positive path', false, e.message)
    end

    # 8) Actuator metrics reachable (management base-path /internal).
    #    Unlike /internal/health, metrics require authentication.
    begin
      res = http_get("#{BACKEND_URL}/internal/metrics", user: admin)
      body = res.body.to_s
      check.call('GET /internal/metrics (admin)',
                 res.code.to_i == 200 && body.include?('names'),
                 "HTTP #{res.code} #{body[0, 80]}")
    rescue StandardError => e
      check.call('GET /internal/metrics (admin)', false, e.message)
    end

    puts
    if failures.empty?
      puts "SMOKE OK (#{BACKEND_URL})"
    else
      abort "SMOKE FAILED: #{failures.join(', ')}"
    end
  end

  namespace :resilience do
    desc 'R01: delete backend pod, assert recovery via /internal/health (SRS 117)'
    task :backend_restart do
      ensure_cluster!
      pod = capture!('kubectl', 'get', 'pods', '-n', NAMESPACE,
                     '-l', 'app=backend', '-o',
                     'jsonpath={.items[0].metadata.name}').strip
      abort 'No backend pod found (is qa:deploy applied?)' if pod.empty?

      restarts = capture!('kubectl', 'get', 'pod', pod, '-n', NAMESPACE,
                          '-o', 'jsonpath={.status.containerStatuses[0].restartCount}').strip
      puts "Deleting backend pod #{pod} (restartCount=#{restarts})..."
      run!('kubectl', 'delete', 'pod', pod, '-n', NAMESPACE)

      deadline = Time.now + 300
      loop do
        begin
          res = http_get("#{BACKEND_URL}/internal/health")
          break if res.code.to_i == 200
        rescue StandardError
          nil
        end
        abort 'Timeout waiting for backend recovery (300s)' if Time.now >= deadline
        sleep 5
        print '.'
      end
      puts "\nR01 PASSED: backend recovered via /internal/health"
    end

    desc 'R02: scale oracle to 0, assert controlled 503 + Unready-no-restart, recover (SRS 117 FT-001)'
    task :db_outage do
      ensure_cluster!
      pod = capture!('kubectl', 'get', 'pods', '-n', NAMESPACE,
                     '-l', 'app=backend', '-o',
                     'jsonpath={.items[0].metadata.name}').strip
      abort 'No backend pod found (is qa:deploy applied?)' if pod.empty?

      before = capture!('kubectl', 'get', 'pod', pod, '-n', NAMESPACE,
                        '-o', 'jsonpath={.status.containerStatuses[0].restartCount}').strip
      puts "Scaling oracle statefulset to 0 (backend pod #{pod}, restartCount=#{before})..."
      run!('kubectl', 'scale', 'statefulset', 'oracle', '-n', NAMESPACE, '--replicas=0')
      begin
        deadline = Time.now + 180
        loop do
          out = capture!('kubectl', 'get', 'statefulset', 'oracle', '-n', NAMESPACE,
                         '-o', 'jsonpath={.status.readyReplicas}')
          break if out.strip.empty? || out.strip == '0'
          abort 'Timeout waiting for oracle to go down (180s)' if Time.now >= deadline
          sleep 5
          print '.'
        end
        puts

        operator = [ENV.fetch('QA_OPERATOR_USER', 'operator'), load_env_file['APP_OPERATOR_PASSWORD']]
        begin
          res = http_post("#{BACKEND_URL}/api/v1/orders",
                          '{"customerId":1,"currency":"USD"}',
                          user: operator, headers: { 'X-Tenant-ID' => 'ACME' })
          unless res.code.to_i == 503
            abort "R02 FAILED: expected 503 during DB outage, got HTTP #{res.code} #{res.body}"
          end
          puts '  PASS  POST /orders -> 503 while DB down'
        rescue StandardError => e
          abort "R02 FAILED: request errored instead of controlled 503: #{e.message}"
        end

        ready = capture!('kubectl', 'get', 'pod', pod, '-n', NAMESPACE,
                         '-o', 'jsonpath={.status.containerStatuses[0].ready}').strip
        after = capture!('kubectl', 'get', 'pod', pod, '-n', NAMESPACE,
                         '-o', 'jsonpath={.status.containerStatuses[0].restartCount}').strip
        unless ready == 'false' && after == before
          abort "R02 FAILED: expected Unready-no-restart (ready=#{ready.inspect}, " \
                "restarts #{before}->#{after})"
        end
        puts '  PASS  backend Unready with no restart during outage'
      ensure
        puts 'Scaling oracle statefulset back to 1...'
        run!('kubectl', 'scale', 'statefulset', 'oracle', '-n', NAMESPACE, '--replicas=1')
      end

      deadline = Time.now + 900
      loop do
        break if oracle_ready?
        abort 'Timeout waiting for oracle to recover (900s)' if Time.now >= deadline
        sleep 10
        print '.'
      end
      puts
      run_sql_as_owner!(File.join(ROOT, 'tests', 'resilience', 'R02_db_outage.sql'),
                        label: 'R02-recovery-probe')
      puts 'R02 PASSED: controlled 503, Unready-no-restart, recovery'
    end
  end

  desc 'SRS 107: run P01-P06 performance workloads, assert Elapsed thresholds'
  task :perf do
    ensure_cluster!
    env = load_env_file
    thresholds = {
      'P01_single_order_latency' => 2,
      'P02_confirm_throughput'   => 5,
      'P03_payment_mix'          => 5,
      'P04_mv_query_latency'     => 5,
      'P05_top_products_scan'    => 10,
      'P06_mixed_read_write'     => 5
    }
    results_dir = File.join(ROOT, 'tests', 'performance', 'results')
    FileUtils.mkdir_p(results_dir)
    failures = []
    runtime_user = env['APP_RUNTIME_USER'] || 'APP_RUNTIME'
    runtime_pwd  = env['APP_RUNTIME_PWD'] || 'LabRuntimePass1'

    thresholds.each do |name, threshold|
      sql = File.join(ROOT, 'tests', 'performance', "#{name}.sql")
      abort "Missing #{sql}" unless File.exist?(sql)
      puts "\n== #{name} (threshold #{threshold}s) =="
      out = begin
        run_sql_as_owner!(sql, label: name, db_user: runtime_user,
                                 db_password: runtime_pwd)
      rescue StandardError => e
        failures << name
        puts "  FAIL  #{name} - #{e.message}"
        next
      end
      File.write(File.join(results_dir, "#{name}.log"), out)
      elapsed = out.to_s.scan(/Elapsed:\s*([0-9:.]+)/).flatten.map { |t|
        t.split(':').inject(0) { |acc, part| acc * 60 + part.to_f }
      }.max
      if elapsed && elapsed <= threshold
        puts "  PASS  #{name} - Elapsed #{elapsed.round(2)}s <= #{threshold}s"
      else
        failures << name
        puts "  FAIL  #{name} - Elapsed #{elapsed.inspect}s > #{threshold}s"
      end
    end

    if failures.empty?
      puts "\nPERF OK (logs in tests/performance/results/)"
    else
      abort "PERF FAILED: #{failures.join(', ')}"
    end
  end

  desc 'Delete workloads + services (keeps cluster and Oracle PVC data)'
  task :down do
    next puts 'Cluster not present - nothing to tear down.' unless cluster_exists?

    run!('kubectl', 'delete', '-k', K8S_DIR, '--ignore-not-found=true')
    # Namespace objects (secret/configmap) live in poc-oracle - remove them too.
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

# =============================================================================
# Multitenant Tasks
# =============================================================================
namespace :multitenant do
  desc 'Verify physical PDB isolation (PDB_CORP vs PDB_OLTP)'
  task :verify do
    sql = <<~SQL
      SET LINESIZE 120 PAGESIZE 50;
      COLUMN name FORMAT A20;
      COLUMN open_mode FORMAT A15;
      SELECT con_id, name, open_mode FROM v$pdbs;
      ALTER SESSION SET CONTAINER = PDB_CORP;
      SELECT tablespace_name, status FROM user_tablespaces;
    SQL
    puts "\n--- Verifying Multitenant PDBs and Physical Tablespaces ---"
    sql_via_pod!("#{sql}\nEXIT SUCCESS\n", 'sys', load_env_file['ORACLE_PASSWORD'],
                 'multitenant-verify', service: 'FREE', sysdba: true)
  end
end


namespace :e2e do
  E2E_DIR = File.join(ROOT, 'tests', 'e2e')
  # id => :pass/:fail, accumulated by run_e2e_test for e2e:all to aggregate.
  $e2e_results = {}

  # Stage tests/ in the Oracle pod once per rake invocation.
  def sync_tests_to_pod!
    return if @tests_synced

    run!('kubectl', 'cp', 'tests', "#{NAMESPACE}/#{oracle_pod!}:/tmp/", chdir: ROOT)
    @tests_synced = true
  end

  def sqlplus_in_pod(conn, script_name)
    sync_tests_to_pod!
    script = "/tmp/tests/e2e/#{script_name}"
    cmd = ['kubectl', 'exec', '-i', '-n', NAMESPACE, oracle_pod!,
           '--', 'bash', '-lc', "sqlplus -L -S #{Shellwords.escape(conn)} @#{script}"]
    puts "\n$ #{redact(cmd.join(' '))}"
    system(*cmd)
    $?.exitstatus == 0
  end

  def run_e2e_test(id, name, conn, script_name)
    puts "\n#{'─' * 70}"
    puts "  RUNNING: [#{id}] #{name}"
    puts "#{'─' * 70}"
    ok = sqlplus_in_pod(conn, script_name)
    puts ok ? "  ✅ PASSED" : "  ❌ FAILED"
    result = ok ? :pass : :fail
    $e2e_results[id] = result
    result
  end

  def e2e_conn(user, pwd, pdb)
    env = load_env_file
    oracle_pwd = env['ORACLE_PASSWORD']
    case user
    when :sys  then "sys/#{oracle_pwd}@localhost:1521/FREE as sysdba"
    when :runtime then "APP_RUNTIME/#{env['APP_RUNTIME_PWD'] || 'LabRuntimePass1'}@localhost:1521/pdb_oltp"
    when :owner   then "APP_OWNER/#{env['APP_OWNER_PWD'] || 'LabOwnerPass1'}@localhost:1521/pdb_oltp"
    end
  end

  desc 'E2E-01: Multitenant physical PDB isolation check (run as SYS)'
  task :physical_isolation do
    run_e2e_test('E01', 'Multitenant Physical Isolation', e2e_conn(:sys, nil, nil),
                 'E01_physical_isolation.sql')
  end

  desc 'E2E-02: VPD fail-closed and cross-tenant isolation'
  task :vpd_fail_closed do
    run_e2e_test('E02', 'VPD Fail-Closed & Isolation', e2e_conn(:runtime, nil, nil), 'E02_vpd_fail_closed.sql')
  end

  desc 'E2E-03: Full PL/SQL order-to-payment business flow'
  task :business_flow do
    run_e2e_test('E03', 'PL/SQL Business Flow', e2e_conn(:runtime, nil, nil), 'E03_plsql_business_flow.sql')
  end

  desc 'E2E-04: Pessimistic locking and inventory consistency'
  task :locking do
    run_e2e_test('E04', 'Pessimistic Locking', e2e_conn(:runtime, nil, nil), 'E04_pessimistic_locking.sql')
  end

  desc 'E2E-05: Materialized view reporting consistency and VPD isolation'
  task :mv_reporting do
    run_e2e_test('E05', 'MV Reporting', e2e_conn(:runtime, nil, nil), 'E05_mv_reporting.sql')
  end

  desc 'E2E-06: Business rules and state machine validation'
  task :business_rules do
    run_e2e_test('E06', 'Business Rules / State Machine', e2e_conn(:runtime, nil, nil), 'E06_business_rules.sql')
  end

  namespace :api do
    desc 'E2E-API: routing (INITECH -> PDB_CORP) - requires backend up'
    task :physical_routing do
      env = load_env_file
      problems = []
      check = lambda do |name, ok, detail = nil|
        if ok
          puts "  PASS  #{name}"
        else
          puts "  FAIL  #{name}#{detail ? " - #{detail}" : ''}"
          problems << name
        end
      end

      begin
        res = http_get("#{BACKEND_URL}/internal/health")
        unless res.code.to_i == 200
          abort "e2e:api:physical_routing needs the backend at #{BACKEND_URL} (GET /internal/health -> HTTP #{res.code})"
        end
      rescue StandardError => e
        abort "e2e:api:physical_routing needs the backend at #{BACKEND_URL} (#{e.message})"
      end

      # 1) Marker order that exists ONLY in the shared PDB (PDB_OLTP).
      #    VPD is fail-closed for APP_OWNER too, so every statement below
      #    opens with pkg_tenant.set_tenant_context.
      seed_out = run_sql_as_owner!(<<~SQL, label: 'api-routing-seed', service: ORACLE_SID)
        SET SERVEROUTPUT ON HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 200
        DECLARE
          v_tenant   NUMBER;
          v_customer NUMBER;
          v_order    NUMBER;
        BEGIN
          SELECT tenant_id, customer_id
            INTO v_tenant, v_customer
            FROM tenant t
            JOIN customer c ON c.tenant_id = t.tenant_id
           WHERE t.tenant_code = 'INITECH'
             AND c.customer_number = 'C0001';
          pkg_tenant.set_tenant_context(v_tenant);
          DELETE FROM order_status_history
           WHERE order_id IN (SELECT order_id FROM sales_order
                               WHERE order_number = 'E2E-ROUTING-0001');
          DELETE FROM sales_order WHERE order_number = 'E2E-ROUTING-0001';
          INSERT INTO sales_order (tenant_id, order_number, customer_id,
                                   status, currency, total_amount)
          VALUES (v_tenant, 'E2E-ROUTING-0001', v_customer, 'DRAFT', 'USD', 0)
          RETURNING order_id INTO v_order;
          COMMIT;
          DBMS_OUTPUT.PUT_LINE('SEED ' || v_order || ' ' || v_customer);
        END;
        /
        EXIT SUCCESS
      SQL
      marker_id  = seed_out.to_s[/SEED\s+(\d+)\s+(\d+)/, 1]
      customer_id = seed_out.to_s[/SEED\s+(\d+)\s+(\d+)/, 2]
      check.call('marker order seeded in shared PDB', !marker_id.nil?,
                 "no SEED line in output")

      begin
      admin = [ENV.fetch('QA_ADMIN_USER', 'admin'), env['APP_ADMIN_PASSWORD']]

      # 2) The shared-only marker must be invisible via the API (404).
      #    A 200 here means the request was routed to PDB_OLTP.
      if marker_id
        begin
          res = http_get("#{BACKEND_URL}/api/v1/orders/#{marker_id}", user: admin,
                         headers: { 'X-Tenant-ID' => 'INITECH' })
          check.call('shared-only marker invisible via API (404)',
                     res.code.to_i == 404, "HTTP #{res.code}")
        rescue StandardError => e
          check.call('shared-only marker invisible via API (404)', false, e.message)
        end
      end

      # 3) A real INITECH order must land in the dedicated PDB (PDB_CORP).
      order_id = nil
      begin
        res = http_post("#{BACKEND_URL}/api/v1/orders",
                        { customerId: customer_id.to_i, currency: 'USD' }.to_json,
                        user: admin,
                        headers: { 'X-Tenant-ID' => 'INITECH' })
        check.call('POST /orders (INITECH) -> 201', res.code.to_i == 201,
                   "HTTP #{res.code} #{res.body.to_s[0, 120]}")
        order_id = JSON.parse(res.body)['orderId'] if res.code.to_i == 201
      rescue StandardError => e
        check.call('POST /orders (INITECH) -> 201', false, e.message)
      end

      # 4) Physical placement: created order in PDB_CORP, not in PDB_OLTP.
      if order_id
        [[DEDICATED_SID, 1], [ORACLE_SID, 0]].each do |service, expected|
          out = run_sql_as_owner!(<<~SQL, label: "api-routing-placement-#{service}", service: service)
            SET SERVEROUTPUT ON HEADING OFF FEEDBACK OFF PAGESIZE 0
            DECLARE
              v_tenant NUMBER;
              v_count  NUMBER;
            BEGIN
              SELECT tenant_id INTO v_tenant FROM tenant WHERE tenant_code = 'INITECH';
              pkg_tenant.set_tenant_context(v_tenant);
              SELECT COUNT(*) INTO v_count FROM sales_order WHERE order_id = #{order_id.to_i};
              DBMS_OUTPUT.PUT_LINE('FOUND ' || v_count);
            END;
            /
            EXIT SUCCESS
          SQL
          found = out.to_s[/FOUND\s+(\d+)/, 1].to_i
          check.call("order #{order_id} in #{service} (expected #{expected})",
                     found == expected, "found #{found}")
        end
      end

      result = problems.empty? ? :pass : :fail
      $e2e_results['API-ROUTING'] = result
      abort "e2e:api:physical_routing failed: #{problems.join('; ')}" unless problems.empty?
      ensure
      # Always remove the marker and any API-created order, even when an
      # HTTP assertion or a database placement query fails.
      [[ORACLE_SID, (marker_id || -1).to_i], [DEDICATED_SID, (order_id || -1).to_i]].each do |service, oid|
        begin
          run_sql_as_owner!(<<~SQL, label: "api-routing-cleanup-#{service}", service: service)
            SET HEADING OFF FEEDBACK OFF PAGESIZE 0
            DECLARE
              v_tenant NUMBER;
            BEGIN
              SELECT tenant_id INTO v_tenant FROM tenant WHERE tenant_code = 'INITECH';
              pkg_tenant.set_tenant_context(v_tenant);
              DELETE FROM order_status_history
               WHERE order_id IN (SELECT order_id FROM sales_order
                                   WHERE order_number = 'E2E-ROUTING-0001')
                  OR order_id = #{oid};
              DELETE FROM sales_order
               WHERE order_number = 'E2E-ROUTING-0001'
                  OR order_id = #{oid};
              COMMIT;
            END;
            /
            EXIT SUCCESS
          SQL
        rescue StandardError => e
          warn "WARNING: cleanup on #{service} failed: #{e.message}"
        end
      end
      end
    end
  end

  desc 'Run the full E2E suite (DB + API) - aborts if any test fails'
  task :all => [
    'e2e:physical_isolation',
    'e2e:vpd_fail_closed',
    'e2e:business_flow',
    'e2e:locking',
    'e2e:mv_reporting',
    'e2e:business_rules',
    'e2e:api:physical_routing'
  ] do
    passed  = $e2e_results.count { |_, v| v == :pass }
    failed  = $e2e_results.select { |_, v| v == :fail }.keys
    puts "\n#{'═' * 70}"
    if failed.empty? && passed == $e2e_results.size && !passed.zero?
      puts "  ✅ E2E suite: #{passed}/#{$e2e_results.size} passed."
      puts "#{'═' * 70}"
    else
      puts "  ❌ E2E suite: #{passed} passed, #{failed.size} failed" \
           "#{failed.empty? ? '' : " (#{failed.join(', ')})"}."
      puts "#{'═' * 70}"
      abort 'E2E suite failed'
    end
  end
end

desc 'List all tasks'
task :default do
  sh 'rake -T'
end
