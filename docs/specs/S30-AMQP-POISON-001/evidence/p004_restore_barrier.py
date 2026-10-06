"""Disposable state model for the S30-06 P-004 restore barrier candidates."""


class RestoreCoordinator:
    def __init__(self, candidate, gates, apps):
        if candidate not in ("readonly-record", "gate-api"):
            raise ValueError("unknown candidate")
        self.candidate = candidate
        self.gates = list(gates)
        self.apps = list(apps)
        self.generation = 0
        self.episode = None
        self.host_inhibited = False
        self.host_control_available = True
        self.observed_episode = {gate: None for gate in self.gates}
        self.gate_paused = {gate: False for gate in self.gates}
        self.gate_bootstrapping = {gate: False for gate in self.gates}
        self.app_drained = {app: False for app in self.apps}

    def write_host_inhibit(self):
        self.generation += 1
        self.episode = "episode-%d" % self.generation
        self.host_inhibited = True
        self.app_drained = {app: False for app in self.apps}
        return self.episode

    def notify(self, gate, delivered):
        if not delivered:
            return
        self.observed_episode[gate] = self.episode
        self.gate_bootstrapping[gate] = False
        # Candidate A gates directly consult the record. Candidate B applies an
        # authenticated, durable command to the gate store.
        self.gate_paused[gate] = self.host_inhibited

    def drain(self, app, confirmed):
        self.app_drained[app] = bool(confirmed and self.host_inhibited)

    def restore_allowed(self, episode):
        if not self.host_control_available or not self.host_inhibited or episode != self.episode:
            return False
        gates_observed = all(
            self.observed_episode[gate] == episode and self.gate_paused[gate]
            for gate in self.gates
        )
        return gates_observed and all(self.app_drained.values())

    def may_issue_permit(self, gate):
        if not self.host_control_available or self.gate_bootstrapping[gate]:
            return False
        if self.observed_episode[gate] != self.episode:
            return self.candidate == "gate-api"
        if self.candidate == "readonly-record":
            return not self.host_inhibited
        return not self.gate_paused[gate] and not self.host_inhibited

    def restart_gate_from_stale_active_store(self, gate):
        # Both candidates must fail closed while bootstrapping current episode;
        # candidate A reads the record, candidate B reconciles it before serving.
        self.observed_episode[gate] = None
        self.gate_paused[gate] = False
        self.gate_bootstrapping[gate] = True

    def release_host_inhibit(self, episode, audited_resume):
        if not audited_resume or episode != self.episode:
            return False
        if not self.restore_allowed(episode):
            return False
        # Audited RESUME commits gate state first; wrapper then releases host record.
        self.host_inhibited = False
        for gate in self.gates:
            self.gate_paused[gate] = False
        self.app_drained = {app: False for app in self.apps}
        return True
