library;

import 'dart:async';
import 'dart:convert';
import 'dart:collection';

const String reactorEnglishLocale = 'en_US';
const List<String> reactorNonEnglishLocales = <String>[
  'de_DE',
  'es_ES',
  'fi_FI',
  'fr_FR',
  'he_IL',
  'it_IT',
  'ja-JP',
  'ko_KR',
  'lt_LT',
  'nl_NL',
  'pl_PL',
  'pt_PT',
  'ru_RU',
  'tr_TR',
  'vi_VI',
  'zh_CN',
  'zh_TW',
];
const List<String> reactorSupportedLocales = <String>[
  reactorEnglishLocale,
  ...reactorNonEnglishLocales,
];

String canonicalReactorLocale(String locale) {
  final String candidate = locale.trim().replaceAll('-', '_').toLowerCase();
  for (final String supported in reactorSupportedLocales) {
    if (supported.replaceAll('-', '_').toLowerCase() == candidate) {
      return supported;
    }
  }
  throw ArgumentError.value(locale, 'locale', 'Unsupported Reactor locale.');
}

enum ReactorText {
  appTitle('app.title', 'React Web'),
  appDescription(
    'app.description',
    'Local-first React server monitoring and control',
  ),
  roleAdmin('role.admin', 'Admin'),
  roleOperator('role.operator', 'Operator'),
  roleViewer('role.viewer', 'Viewer'),
  statusLive('status.live', 'Live'),
  statusConnecting('status.connecting', 'Connecting'),
  statusDegraded('status.degraded', 'Degraded'),
  statusOffline('status.offline', 'Offline'),
  statusNominal('status.nominal', 'Nominal'),
  statusNormal('status.normal', 'Normal'),
  statusElevated('status.elevated', 'Elevated'),
  statusCritical('status.critical', 'Critical'),
  commonNoActivity('common.no_activity', 'No activity'),
  commonPlayers('common.players', 'Players'),
  commonEntities('common.entities', 'Entities'),
  commonChunks('common.chunks', 'Chunks'),
  commonTickTime('common.tick_time', 'Tick Time'),
  commonIncidentScore('common.incident_score', 'Incident Score'),
  commonMemoryUsed('common.memory_used', 'Memory Used'),
  commonGcTime('common.gc_time', 'GC Time'),
  commonTopWorldMspt('common.top_world_mspt', 'Top World MSPT'),
  commonTopChunkCost('common.top_chunk_cost', 'Top Chunk Cost'),
  commonQueue('common.queue', 'Queue'),
  commonQueueTime('common.queue_time', 'Queue Time'),
  commonBudget('common.budget', 'Budget'),
  commonProcessLoad('common.process_load', 'Process Load'),
  commonEventTime('common.event_time', 'Event Time'),
  commonPingP95('common.ping_p95', 'Ping p95'),
  commonIncidentTimeline('common.incident_timeline', 'Incident Timeline'),
  commonSchedulerBacklog('common.scheduler_backlog', 'Scheduler Backlog'),
  commonGrowthRate('common.growth_rate', 'Growth Rate'),
  commonRedstone('common.redstone', 'Redstone'),
  commonHoppers('common.hoppers', 'Hoppers'),
  commonPhysics('common.physics', 'Physics'),
  commonFluid('common.fluid', 'Fluid'),
  commonOptimizations('common.optimizations', 'Optimizations'),
  configNoOptions('config.no_options', 'No configurable options'),
  configNoTunableKnobs(
    'config.no_tunable_knobs',
    'This item has no tunable knobs.',
  ),
  chunksTitle('screen.chunks.title', 'Chunks'),
  chunksSubtitle('screen.chunks.subtitle', 'Chunk loading and persistence'),
  chunksLoadMs('screen.chunks.series.load_ms', 'Load ms'),
  chunksGenMs('screen.chunks.series.gen_ms', 'Gen ms'),
  chunksLoadGenTime('screen.chunks.load_gen_time', 'Chunk Load/Gen Time'),
  chunksLoadedPerSecond('screen.chunks.loaded_per_second', 'Loaded/s'),
  chunksGeneratedPerSecond('screen.chunks.generated_per_second', 'Generated/s'),
  chunksLoadTime('screen.chunks.load_time', 'Load Time'),
  chunksGenTime('screen.chunks.gen_time', 'Gen Time'),
  chunksPersistence('screen.chunks.persistence', 'Persistence'),
  chunksWorldSave('screen.chunks.world_save', 'World Save'),
  chunksPdcBatcher('screen.chunks.pdc_batcher', 'PDC Batcher'),
  entitiesTitle('screen.entities.title', 'Entities'),
  entitiesSubtitle('screen.entities.subtitle', 'Entity counts and AI load'),
  entitiesCount('screen.entities.count', 'Entity Count'),
  entitiesPlayerPing('screen.entities.player_ping', 'Player Ping'),
  entitiesAiActive('screen.entities.ai_active', 'AI Active'),
  entitiesSpawnsPerSecond('screen.entities.spawns_per_second', 'Spawns/s'),
  entitiesPingJitter('screen.entities.ping_jitter', 'Ping Jitter'),
  entitiesJitter('screen.entities.series.jitter', 'Jitter'),
  eventsTitle('screen.events.title', 'Events'),
  eventsSubtitle('screen.events.subtitle', 'Event dispatch and listeners'),
  eventsHandlesPerTick('screen.events.handles_per_tick', 'Handles/Tick'),
  eventsSeriesHandlesPerTick(
    'screen.events.series.handles_per_tick',
    'Handles/tick',
  ),
  eventsListeners('screen.events.listeners', 'Listeners'),
  worldsTitle('screen.worlds.title', 'Worlds'),
  worldsSubtitle('screen.worlds.subtitle', 'Per-world performance'),
  worldsPerWorldTick('screen.worlds.per_world_tick', 'Per-World Tick'),
  worldsPerWorldTickTime(
    'screen.worlds.per_world_tick_time',
    'Per-World Tick Time',
  ),
  worldsBreakdown('screen.worlds.breakdown', 'Per-World Breakdown'),
  worldsBudgetsMoved(
    'screen.worlds.budgets_moved',
    'Per-world tick budgets moved to World Overrides',
  ),
  worldsBudgetsMovedDescription(
    'screen.worlds.budgets_moved_description',
    'Open the World Overrides screen to view per-world NORMAL/PRESSURE/PANIC state and edit tick budgets.',
  ),
  performanceTitle('screen.performance.title', 'Performance'),
  performanceSubtitle(
    'screen.performance.subtitle',
    'Tick timing and load hotspots',
  ),
  performanceSpikeRate('screen.performance.spike_rate', 'Spike Rate'),
  performanceTickDuration('screen.performance.tick_duration', 'Tick Duration'),
  performanceTickSpikeRate(
    'screen.performance.tick_spike_rate',
    'Tick Spike Rate',
  ),
  metricsTitle('screen.metrics.title', 'Metrics'),
  metricsSubtitle(
    'screen.metrics.subtitle',
    'Every sampler currently published by this React server',
  ),
  metricsCatalog('screen.metrics.catalog', 'Live Sampler Catalog'),
  metricsCatalogDescription(
    'screen.metrics.catalog_description',
    'Search the complete live snapshot by sampler name, identifier, or unit.',
  ),
  metricsVisibleCount(
    'screen.metrics.visible_count',
    'Samplers: {visible}/{total}',
    <String>{'visible', 'total'},
  ),
  metricsSearchPlaceholder(
    'screen.metrics.search_placeholder',
    'Filter samplers…',
  ),
  metricsWaiting('screen.metrics.waiting', 'Waiting for telemetry'),
  metricsWaitingDescription(
    'screen.metrics.waiting_description',
    'Sampler data will appear when the server publishes its first snapshot.',
  ),
  metricsSequence('screen.metrics.sequence', 'Snapshot #{sequence}', <String>{
    'sequence',
  }),
  metricsNoMatches(
    'screen.metrics.no_matches',
    'No samplers match this filter.',
  ),
  metricsSampler('screen.metrics.sampler', 'Sampler'),
  metricsCurrent('screen.metrics.current', 'Current'),
  metricsMinimum('screen.metrics.minimum', 'Minimum'),
  metricsMaximum('screen.metrics.maximum', 'Maximum'),
  metricsSamples('screen.metrics.samples', 'Samples'),
  memoryTitle('screen.memory.title', 'Memory'),
  memorySubtitle('screen.memory.subtitle', 'Heap usage and garbage collection'),
  memoryFree('screen.memory.free', 'Memory Free'),
  memoryAfterGc('screen.memory.after_gc', 'After GC'),
  memoryPressure('screen.memory.pressure', 'Memory Pressure'),
  memoryGcPauseP95('screen.memory.gc_pause_p95', 'GC Pause p95'),
  memoryHeapUsage('screen.memory.heap_usage', 'Heap Usage'),
  memoryGarbage('screen.memory.garbage', 'Memory Garbage'),
  memoryGcTimePercent('screen.memory.gc_time_percent', 'GC Time %'),
  internalsTitle('screen.internals.title', 'Internals'),
  internalsSubtitle(
    'screen.internals.subtitle',
    'Engine internals and job queues',
  ),
  internalsAsync('screen.internals.async', 'Async'),
  internalsSync('screen.internals.sync', 'Sync'),
  internalsReactTickTime('screen.internals.react_tick_time', 'React Tick Time'),
  internalsJobs('screen.internals.jobs', 'Jobs'),
  internalsCpuLoad('screen.internals.cpu_load', 'CPU Load'),
  internalsSystemLoad('screen.internals.system_load', 'System Load'),
  internalsOutsideLoad('screen.internals.outside_load', 'Outside Load'),
  overviewTitle('screen.overview.title', 'Overview'),
  overviewSubtitle(
    'screen.overview.subtitle',
    'Live server health and key runtime telemetry',
  ),
  overviewVitals('screen.overview.vitals', 'Vitals'),
  overviewTps('screen.overview.tps', 'TPS'),
  overviewNoIncidentHistory(
    'screen.overview.no_incident_history',
    'No incident history yet',
  ),
  overviewIncidentPressure(
    'screen.overview.incident_pressure',
    'Incident Pressure',
  ),
  mechanicsTitle('screen.mechanics.title', 'Mechanics'),
  mechanicsSubtitle('screen.mechanics.subtitle', 'Game mechanic optimizations'),
  mechanicsRedstoneTickTime(
    'screen.mechanics.redstone_tick_time',
    'Redstone Tick Time',
  ),
  mechanicsBurstRate('screen.mechanics.burst_rate', 'Burst Rate'),
  mechanicsChainCoalescing(
    'screen.mechanics.chain_coalescing',
    'Chain Coalescing',
  ),
  mechanicsPhysicsFluids('screen.mechanics.physics_fluids', 'Physics & Fluids'),
  mechanicsPhysicsTickTime(
    'screen.mechanics.physics_tick_time',
    'Physics Tick Time',
  ),
  mechanicsFluidTickTime('screen.mechanics.fluid_tick_time', 'Fluid Tick Time'),
  mechanicsCropFastForward(
    'screen.mechanics.crop_fast_forward',
    'Crop Fast-Forward',
  ),
  mechanicsLazyGravitySkipped(
    'screen.mechanics.lazy_gravity_skipped',
    'Lazy Gravity Skipped',
  ),
  mechanicsSpawnerLightCacheSkipped(
    'screen.mechanics.spawner_light_cache_skipped',
    'Spawner Light Cache Skipped',
  ),
  mechanicsExplosionPacketReduction(
    'screen.mechanics.explosion_packet_reduction',
    'Explosion Packet Reduction',
  ),
  incidentsTitle('screen.incidents.title', 'Incidents'),
  incidentsSubtitle(
    'screen.incidents.subtitle',
    'Incident scoring and history',
  ),
  incidentsBacklog('screen.incidents.backlog', 'Backlog'),
  incidentsBacklogGrowth('screen.incidents.backlog_growth', 'Backlog Growth'),
  commonUpdateFailed('common.update_failed', 'Update failed'),
  commonRequiresAdminRole('common.requires_admin_role', 'Requires admin role'),
  commonEnabled('common.enabled', 'Enabled'),
  commonDisabled('common.disabled', 'Disabled'),
  commonLoadingWorlds('common.loading_worlds', 'Loading worlds...'),
  logsTitle('screen.logs.title', 'Logs'),
  logsSubtitle('screen.logs.subtitle', 'Live server log stream'),
  logsResume('screen.logs.resume', 'Resume'),
  logsPause('screen.logs.pause', 'Pause'),
  logsClear('screen.logs.clear', 'Clear'),
  logsStream('screen.logs.stream', 'Stream'),
  logsLevel('screen.logs.level', 'Level'),
  logsLiveRequired(
    'screen.logs.live_required',
    'Logs require a live connection.',
  ),
  consoleTitle('screen.logs.console.title', 'Server Console'),
  consoleDescription(
    'screen.logs.console.description',
    'Dispatch a command through the authenticated React control channel.',
  ),
  consolePlaceholder(
    'screen.logs.console.placeholder',
    'Enter a server command without /',
  ),
  consoleRun('screen.logs.console.run', 'Run Command'),
  consoleRunning('screen.logs.console.running', 'Dispatching…'),
  consoleAdminRequired(
    'screen.logs.console.admin_required',
    'Console execution requires the console:execute scope.',
  ),
  consoleDispatched('screen.logs.console.dispatched', 'Command dispatched'),
  consoleRejected(
    'screen.logs.console.rejected',
    'The server rejected the command.',
  ),
  consoleFailed('screen.logs.console.failed', 'Command failed'),
  integrationsTitle('screen.integrations.title', 'Integrations'),
  integrationsSubtitle(
    'screen.integrations.subtitle',
    'Detected plugin integrations',
  ),
  integrationsNone('screen.integrations.none', 'No integrations detected'),
  integrationsNoneDescription(
    'screen.integrations.none_description',
    'No Adapt, Iris, or Wormholes metrics are being reported by this server.',
  ),
  integrationsPolicyLatency(
    'screen.integrations.policy_latency',
    'Policy Latency',
  ),
  integrationsChunkStreamMs(
    'screen.integrations.chunk_stream_ms',
    'Chunk Stream ms',
  ),
  integrationsProjectionRenderMs(
    'screen.integrations.projection_render_ms',
    'Projection Render ms',
  ),
  heatmapsTitle('screen.heatmaps.title', 'Heatmaps'),
  heatmapsSubtitle('screen.heatmaps.subtitle', 'Spatial load distribution'),
  heatmapsEntityPressure('screen.heatmaps.entity_pressure', 'Entity Pressure'),
  heatmapsChunkLoadGenCost(
    'screen.heatmaps.chunk_load_gen_cost',
    'Chunk Load/Gen Cost',
  ),
  heatmapsChunkSampler('screen.heatmaps.chunk_sampler', 'Chunk Sampler'),
  heatmapsRedstoneActivity(
    'screen.heatmaps.redstone_activity',
    'Redstone Activity',
  ),
  heatmapsHopperThroughput(
    'screen.heatmaps.hopper_throughput',
    'Hopper Throughput',
  ),
  heatmapsTickSpikeOrigin(
    'screen.heatmaps.tick_spike_origin',
    'Tick-Spike Origin',
  ),
  heatmapsEventImpactPie(
    'screen.heatmaps.event_impact_pie',
    'Event Impact (pie)',
  ),
  heatmapsEventImpactList(
    'screen.heatmaps.event_impact_list',
    'Event Impact (list)',
  ),
  heatmapsIrisBiomeShare(
    'screen.heatmaps.iris_biome_share',
    'Iris Biome Share',
  ),
  heatmapsIrisWorldShare(
    'screen.heatmaps.iris_world_share',
    'Iris World Share',
  ),
  heatmapsSpatialMetrics('screen.heatmaps.spatial_metrics', 'Spatial Metrics'),
  heatmapsChunkHeatmaps('screen.heatmaps.chunk_heatmaps', 'Chunk Heatmaps'),
  heatmapsLoading('screen.heatmaps.loading', 'Loading heatmaps...'),
  heatmapsWorld('screen.heatmaps.world', 'World'),
  heatmapsCenterChunkX('screen.heatmaps.center_chunk_x', 'Center chunk X'),
  heatmapsCenterChunkZ('screen.heatmaps.center_chunk_z', 'Center chunk Z'),
  heatmapsRadiusChunks('screen.heatmaps.radius_chunks', 'Radius (chunks)'),
  heatmapsLiveRequired(
    'screen.heatmaps.live_required',
    'Grid heatmaps require a live connection.',
  ),
  worldOverridesTitle('screen.world_overrides.title', 'World Overrides'),
  worldOverridesSubtitle(
    'screen.world_overrides.subtitle',
    'Per-world tick budgets',
  ),
  worldOverridesNoWorlds('screen.world_overrides.no_worlds', 'No worlds'),
  worldOverridesNoWorldsDescription(
    'screen.world_overrides.no_worlds_description',
    'No worlds reported by the server.',
  ),
  worldOverridesBudgetMs('screen.world_overrides.budget_ms', 'Budget (ms)'),
  worldOverridesPanicMs('screen.world_overrides.panic_ms', 'Panic (ms)'),
  worldOverridesReleaseMs('screen.world_overrides.release_ms', 'Release (ms)'),
  worldOverridesSection(
    'screen.world_overrides.section',
    'Per-World Overrides',
  ),
  worldOverridesLiveRequired(
    'screen.world_overrides.live_required',
    'Per-world overrides require a live connection.',
  ),
  governorsTitle('screen.governors.title', 'Governors'),
  governorsSubtitle('screen.governors.subtitle', 'Adaptive load governors'),
  governorsBacklogGrowthRate(
    'screen.governors.backlog_growth_rate',
    'Backlog Growth Rate',
  ),
  governorsSection('screen.governors.section', 'Governors'),
  governorsControl('screen.governors.control', 'Governor Control'),
  governorsLiveRequired(
    'screen.governors.live_required',
    'Governor control requires a live connection.',
  ),
  tweaksTitle('screen.tweaks.title', 'Tweaks'),
  tweaksSubtitle('screen.tweaks.subtitle', 'Fine-grained runtime tweaks'),
  tweaksConfigure('screen.tweaks.configure', 'Configure ({count})', <String>{
    'count',
  }),
  tweaksControl('screen.tweaks.control', 'Tweak Control'),
  tweaksLiveRequired(
    'screen.tweaks.live_required',
    'Tweak control requires a live connection.',
  ),
  tweaksLoading('screen.tweaks.loading', 'Loading tweaks...'),
  actionsTitle('screen.actions.title', 'Actions'),
  actionsSubtitle('screen.actions.subtitle', 'Operational commands'),
  actionsExecute('screen.actions.execute', 'Execute'),
  actionsDestructive('screen.actions.destructive', 'Destructive'),
  actionsConfirmTitle(
    'screen.actions.confirm_title',
    'Confirm {action}',
    <String>{'action'},
  ),
  actionsConfirmDestructive(
    'screen.actions.confirm_destructive',
    'This is a destructive action. Are you sure you want to proceed?',
  ),
  actionsRecentExecutions(
    'screen.actions.recent_executions',
    'Recent Executions',
  ),
  actionsNoneExecuted(
    'screen.actions.none_executed',
    'No actions executed yet.',
  ),
  actionsExecutionSummary(
    'screen.actions.execution_summary',
    '{actionId} — {status} — {ticketId}',
    <String>{'actionId', 'status', 'ticketId'},
  ),
  actionsQueued('screen.actions.queued', 'Action queued'),
  actionsFailed('screen.actions.failed', 'Action failed'),
  actionsLiveRequired(
    'screen.actions.live_required',
    'Actions require a live connection.',
  ),
  actionsLoading('screen.actions.loading', 'Loading actions...'),
  configEditorTitle('screen.config_editor.title', 'Config Editor'),
  configEditorSubtitle('screen.config_editor.subtitle', 'Server configuration'),
  configEditorApplyChanges(
    'screen.config_editor.apply_changes',
    'Apply Changes',
  ),
  configEditorPresets('screen.config_editor.presets', 'Presets'),
  configEditorPresetOff('screen.config_editor.preset.off', 'Off'),
  configEditorPresetLight('screen.config_editor.preset.light', 'Light'),
  configEditorPresetBalanced(
    'screen.config_editor.preset.balanced',
    'Balanced',
  ),
  configEditorPresetHigh('screen.config_editor.preset.high', 'High'),
  configEditorFailed('screen.config_editor.failed', 'Config failed'),
  configEditorApplied('screen.config_editor.applied', 'Configuration applied'),
  configEditorLiveRequired(
    'screen.config_editor.live_required',
    'Config editing requires a live connection.',
  ),
  configEditorLoading(
    'screen.config_editor.loading',
    'Loading configuration...',
  ),
  optimizationTitle('screen.optimization.title', 'Optimization'),
  optimizationEnabledCount(
    'screen.optimization.enabled_count',
    'Enabled: {enabled}/{total}',
    <String>{'enabled', 'total'},
  ),
  optimizationEnableAll('screen.optimization.enable_all', 'Enable all'),
  optimizationDisableAll('screen.optimization.disable_all', 'Disable all'),
  optimizationCategoryCount(
    'screen.optimization.category_count',
    'On: {enabled}/{total}',
    <String>{'enabled', 'total'},
  ),
  optimizationConfigure('screen.optimization.configure', 'Configure'),
  optimizationRuntimeControl(
    'screen.optimization.runtime_control',
    'Runtime feature control',
  ),
  optimizationFeatureControl(
    'screen.optimization.feature_control',
    'Feature Control',
  ),
  optimizationLiveRequired(
    'screen.optimization.live_required',
    'Feature control requires a live connection.',
  ),
  optimizationLoading('screen.optimization.loading', 'Loading features...'),
  comparisonTitle('screen.comparison.title', 'Comparison'),
  comparisonSubtitle(
    'screen.comparison.subtitle',
    'Cross-server metric comparison',
  ),
  comparisonOverlay('screen.comparison.overlay', 'Overlay'),
  comparisonNoData('screen.comparison.no_data', 'No data for selected metric'),
  comparisonLeaderboard('screen.comparison.leaderboard', 'Leaderboard'),
  comparisonMetric('screen.comparison.metric', 'Metric'),
  comparisonServers('screen.comparison.servers', 'Servers'),
  incidentCenterTitle('screen.incident_center.title', 'Incident Center'),
  incidentCenterSubtitle(
    'screen.incident_center.subtitle',
    'Live incident analysis',
  ),
  incidentCenterContributingFactors(
    'screen.incident_center.contributing_factors',
    'Contributing Factors',
  ),
  incidentCenterLiveRequired(
    'screen.incident_center.live_required',
    'Incident data requires a live connection.',
  ),
  incidentCenterLoading(
    'screen.incident_center.loading',
    'Loading incidents...',
  ),
  environmentTitle('screen.environment.title', 'Environment'),
  environmentSubtitle(
    'screen.environment.subtitle',
    'Host and runtime diagnostics',
  ),
  environmentLiveRequired(
    'screen.environment.live_required',
    'Environment data requires a live connection.',
  ),
  environmentLoading('screen.environment.loading', 'Loading diagnostics...'),
  environmentNoData(
    'screen.environment.no_data',
    'No environment data available.',
  ),
  environmentRefresh('screen.environment.refresh', 'Refresh'),
  commonAll('common.all', 'All'),
  commonHealthy('common.healthy', 'Healthy'),
  commonWarning('common.warning', 'Warning'),
  commonNever('common.never', 'Never'),
  commonSecondsAgo('common.seconds_ago', '{value}s ago', <String>{'value'}),
  commonMinutesAgo('common.minutes_ago', '{value}m ago', <String>{'value'}),
  commonHoursAgo('common.hours_ago', '{value}h ago', <String>{'value'}),
  commonDaysAgo('common.days_ago', '{value}d ago', <String>{'value'}),
  fleetTitle('screen.fleet.title', 'Server group'),
  fleetNoServersPaired('screen.fleet.no_servers_paired', 'No servers paired'),
  fleetAllServersNominal(
    'screen.fleet.all_servers_nominal',
    'Nominal server count: {count}',
    <String>{'count'},
  ),
  fleetServersNeedAttention(
    'screen.fleet.servers_need_attention',
    'Server count: {total} · Need attention: {attention}',
    <String>{'total', 'attention'},
  ),
  fleetHealth('screen.fleet.health', 'Server group health'),
  fleetTag('screen.fleet.tag', 'Tag'),
  fleetMeanTps('screen.fleet.mean_tps', 'Mean TPS'),
  fleetWorstTps('screen.fleet.worst_tps', 'Worst TPS'),
  fleetCompositeHealth('screen.fleet.composite_health', 'Composite Health'),
  fleetTotalPlayers('screen.fleet.total_players', 'Total Players'),
  fleetWorstMspt('screen.fleet.worst_mspt', 'Worst MSPT'),
  fleetCriticalCount(
    'screen.fleet.critical_count',
    'Critical: {count}',
    <String>{'count'},
  ),
  fleetWarningCount('screen.fleet.warning_count', 'Warning: {count}', <String>{
    'count',
  }),
  fleetInfoCount('screen.fleet.info_count', 'Info: {count}', <String>{'count'}),
  fleetServers('screen.fleet.servers', 'Servers'),
  fleetPairedCount('screen.fleet.paired_count', 'Paired: {count}', <String>{
    'count',
  }),
  fleetNeedsAttention('screen.fleet.needs_attention', 'Needs Attention'),
  fleetAllHealthy('screen.fleet.all_healthy', 'All servers healthy'),
  fleetAlerts('screen.fleet.alerts', 'Alerts'),
  fleetLastSeen('screen.fleet.last_seen', 'Last Seen'),
  fleetOpenDashboard('screen.fleet.open_dashboard', 'Open dashboard'),
  fleetAlertCount(
    'screen.fleet.alert_count',
    'Open alert count: {count}',
    <String>{'count'},
  ),
  alertsTitle('screen.alerts.title', 'Alerts'),
  alertsSubtitle('screen.alerts.subtitle', 'Open server-group alerts'),
  alertsNoneOpen('screen.alerts.none_open', 'No open alerts'),
  alertsSeverityCritical('screen.alerts.severity.critical', 'critical'),
  alertsSeverityWarning('screen.alerts.severity.warning', 'warning'),
  alertsSeverityInfo('screen.alerts.severity.info', 'info'),
  alertsFirstSeen('screen.alerts.first_seen', 'First seen: {time}', <String>{
    'time',
  }),
  alertsAck('screen.alerts.ack', 'Ack'),
  alertsAcked('screen.alerts.acked', 'Acked'),
  alertsResolve('screen.alerts.resolve', 'Resolve'),
  alertLowTps('alert.low_tps', 'Low TPS'),
  alertHighMspt('alert.high_mspt', 'High MSPT'),
  alertElevatedIncidentScore(
    'alert.elevated_incident_score',
    'Elevated incident score',
  ),
  alertHighGcTime('alert.high_gc_time', 'High GC time'),
  alertHighPingP95('alert.high_ping_p95', 'High ping p95'),
  alertMemoryPressure('alert.memory_pressure', 'Memory pressure'),
  alertCriticalNotification('alert.critical_notification', 'Critical alert'),
  alertCriticalDescription(
    'alert.critical_description',
    '{server}: {title}',
    <String>{'server', 'title'},
  ),
  settingsTitle('screen.settings.title', 'Settings'),
  settingsSubtitle(
    'screen.settings.subtitle',
    'Appearance, alert thresholds, and server connections',
  ),
  settingsAppearance('screen.settings.appearance', 'Appearance'),
  settingsAppearanceDescription(
    'screen.settings.appearance_description',
    'Choose the interface theme used on this browser.',
  ),
  settingsDarkTheme('screen.settings.dark_theme', 'Dark'),
  settingsDarkThemeDescription(
    'screen.settings.dark_theme_description',
    'Low-glare workspace for dim environments.',
  ),
  settingsLightTheme('screen.settings.light_theme', 'Light'),
  settingsLightThemeDescription(
    'screen.settings.light_theme_description',
    'High-clarity workspace for bright environments.',
  ),
  themeSwitchToLight('theme.switch_to_light', 'Switch to light theme'),
  themeSwitchToDark('theme.switch_to_dark', 'Switch to dark theme'),
  settingsThresholdsSaved(
    'screen.settings.thresholds_saved',
    'Thresholds saved',
  ),
  settingsFleetCleared(
    'screen.settings.fleet_cleared',
    'Saved server group cleared',
  ),
  settingsNothingToExport(
    'screen.settings.nothing_to_export',
    'Nothing to export',
  ),
  settingsNoServersConfigured(
    'screen.settings.no_servers_configured',
    'No servers configured.',
  ),
  settingsFleetImported(
    'screen.settings.fleet_imported',
    'Server group imported',
  ),
  settingsServersLoaded(
    'screen.settings.servers_loaded',
    'Loaded server count: {count}.',
    <String>{'count'},
  ),
  settingsFleetUnavailable(
    'screen.settings.fleet_unavailable',
    'Server group unavailable',
  ),
  settingsFleetNotInitialized(
    'screen.settings.fleet_not_initialized',
    'No server group has been initialized.',
  ),
  settingsAccountRoles('screen.settings.account_roles', 'Account / Roles'),
  settingsAlertThresholds(
    'screen.settings.alert_thresholds',
    'Alert Thresholds',
  ),
  settingsTpsWarn('screen.settings.tps_warn', 'TPS Warn'),
  settingsTpsCritical('screen.settings.tps_critical', 'TPS Critical'),
  settingsMsptWarn('screen.settings.mspt_warn', 'MSPT Warn'),
  settingsIncidentScoreWarn(
    'screen.settings.incident_score_warn',
    'Incident Score Warn',
  ),
  settingsGcPercentWarn('screen.settings.gc_percent_warn', 'GC Percent Warn'),
  settingsPingP95Warn('screen.settings.ping_p95_warn', 'Ping P95 Warn'),
  settingsMemoryPressureWarn(
    'screen.settings.memory_pressure_warn',
    'Memory Pressure Warn',
  ),
  settingsSaveThresholds('screen.settings.save_thresholds', 'Save thresholds'),
  settingsResetDefaults('screen.settings.reset_defaults', 'Reset to defaults'),
  settingsSavedServers('screen.settings.saved_servers', 'Saved Servers'),
  settingsConfirmClearAll(
    'screen.settings.confirm_clear_all',
    'Confirm clear all',
  ),
  settingsClearAll('screen.settings.clear_all', 'Clear all'),
  settingsExportConnections(
    'screen.settings.export_connections',
    'Export connections',
  ),
  settingsImportConnections(
    'screen.settings.import_connections',
    'Import connections',
  ),
  settingsExportSecurity(
    'screen.settings.export_security',
    'Export files contain bearer tokens and credentials. Store them securely.',
  ),
  settingsImportFailed(
    'screen.settings.import_failed',
    'The connection file could not be imported.',
  ),
  settingsReplaceFleet(
    'screen.settings.replace_fleet',
    'Replace server group?',
  ),
  settingsReplaceFleetMessage(
    'screen.settings.replace_fleet_message',
    'This will replace the current server group ({current}) with the servers from the file ({incoming}).',
    <String>{'current', 'incoming'},
  ),
  settingsMalformedSkipped(
    'screen.settings.malformed_skipped',
    ' Malformed entry count to skip: {count}.',
    <String>{'count'},
  ),
  fleetImportInvalidJson('fleet_import.invalid_json', 'Invalid JSON'),
  fleetImportInvalidFile(
    'fleet_import.invalid_file',
    'Not a valid server-group export file',
  ),
  fleetImportWrongKind(
    'fleet_import.wrong_kind',
    'Not a reactor-fleet server export file',
  ),
  fleetImportInvalidServerList(
    'fleet_import.invalid_server_list',
    'Missing or invalid servers list',
  ),
  fleetImportNoValidServers(
    'fleet_import.no_valid_servers',
    'No valid servers found. Malformed entry count: {count}',
    <String>{'count'},
  ),
  fleetImportNoServers('fleet_import.no_servers', 'No servers in file'),
  settingsImport('screen.settings.import', 'Import'),
  settingsServerLabel('screen.settings.server_label', 'Server label'),
  settingsRename('screen.settings.rename', 'Rename'),
  settingsRemove('screen.settings.remove', 'Remove'),
  settingsRemoveServer(
    'screen.settings.remove_server',
    'Remove {server}?',
    <String>{'server'},
  ),
  settingsRemoveServerMessage(
    'screen.settings.remove_server_message',
    'This will disconnect and remove the server from your server group.',
  ),
  settingsAddTag('screen.settings.add_tag', 'Add tag'),
  addServerTitle('screen.add_server.title', 'Add Server'),
  addServerSubtitle(
    'screen.add_server.subtitle',
    'Paste an authenticated RCT2 code from the React server console',
  ),
  addServerPasteFullCode(
    'screen.add_server.paste_full_code',
    'Paste the full RCT2 pairing code.',
  ),
  addServerPrefixRequired(
    'screen.add_server.prefix_required',
    'Pairing codes must start with RCT2.',
  ),
  addServerPayloadMissing(
    'screen.add_server.payload_missing',
    'The RCT2 payload is missing.',
  ),
  addServerCodeIncomplete(
    'screen.add_server.code_incomplete',
    'This code is incomplete. Copy the entire Pairing code line from the server console.',
  ),
  addServerDecodeFailed(
    'screen.add_server.decode_failed',
    'This RCT2 code could not be decoded. Copy the full code without truncating it.',
  ),
  addServerFleetClearedMessage(
    'screen.add_server.fleet_cleared_message',
    'Saved server group cleared. Paste a new RCT2 code to reconnect.',
  ),
  addServerFleetReset('screen.add_server.fleet_reset', 'Server group reset'),
  addServerConnectingIdentity(
    'screen.add_server.connecting_identity',
    'Connecting to server identity endpoint...',
  ),
  addServerPaired('screen.add_server.paired', 'Server paired'),
  addServerInvalidPairingCode(
    'screen.add_server.invalid_pairing_code',
    'Invalid pairing code',
  ),
  addServerInvalidPairingMessage(
    'screen.add_server.invalid_pairing_message',
    'Invalid pairing code. Copy the full RCT2 line from the server console.',
  ),
  addServerInvalidPairingDescription(
    'screen.add_server.invalid_pairing_description',
    'Check that you copied the full RCT2 code from the server console.',
  ),
  addServerConnectionFailedMessage(
    'screen.add_server.connection_failed_message',
    'Could not connect to the server. Verify the advertised direct or relay transport and try a fresh pairing code.',
  ),
  addServerPairingFailed('screen.add_server.pairing_failed', 'Pairing failed'),
  addServerClearCode('screen.add_server.clear_code', 'Clear code'),
  addServerConfirmReset('screen.add_server.confirm_reset', 'Confirm reset'),
  addServerResetFleet('screen.add_server.reset_fleet', 'Reset server group'),
  addServerConnecting('screen.add_server.connecting', 'Connecting...'),
  addServerPair('screen.add_server.pair', 'Pair'),
  addServerPairingConsole(
    'screen.add_server.pairing_console',
    'Pairing Console',
  ),
  addServerPairingConsoleDescription(
    'screen.add_server.pairing_console_description',
    'Connect direct LAN nodes or relay-backed servers.',
  ),
  addServerHandshake('screen.add_server.handshake', 'RCT2 handshake'),
  addServerNeedsFullCode(
    'screen.add_server.needs_full_code',
    'Needs full code',
  ),
  addServerStandby('screen.add_server.standby', 'Standby'),
  addServerDecoded('screen.add_server.decoded', 'Decoded'),
  addServerInputPlaceholder(
    'screen.add_server.input_placeholder',
    'Paste RCT2. code from server console',
  ),
  addServerInputHelper(
    'screen.add_server.input_helper',
    'You can paste the raw RCT2 token or the full console line.',
  ),
  addServerConnectionFlow('screen.add_server.connection_flow', 'How to pair'),
  addServerConnectionFlowDescription(
    'screen.add_server.connection_flow_description',
    'Complete these steps on the Minecraft server, then paste its scoped connection code below.',
  ),
  addServerCopy('screen.add_server.copy', 'Make React reachable'),
  addServerCopyDescription(
    'screen.add_server.copy_description',
    'React defaults to listenerEnabled = true and listens on all interfaces. Forward TCP 9696 to the server, then advertise its reachable URL or enter that URL as Direct host. Hosted HTTPS dashboards require HTTPS or a WSS relay.',
  ),
  addServerDecode('screen.add_server.decode', 'Generate a scoped code'),
  addServerDecodeDescription(
    'screen.add_server.decode_description',
    'Run the pairing command in game as an OP or player with react.use, or omit the leading slash in the dedicated server console. Choose viewer, operator, or admin; viewer is the default.',
  ),
  addServerMonitor('screen.add_server.monitor', 'Paste and review'),
  addServerMonitorDescription(
    'screen.add_server.monitor_description',
    'In game, click the pairing-code copy action. From the server console, copy the complete RCT2 value. Paste it below, then check the transport and fingerprint.',
  ),
  addServerSecurity('screen.add_server.security', 'Connect and retain control'),
  addServerSecurityDescription(
    'screen.add_server.security_description',
    'Select Pair to open the live workspace. Credentials stay in this browser. The pairing output also prints the token ID used to revoke access later.',
  ),
  addServerCredentialScope(
    'screen.add_server.credential_scope',
    'Credential scope',
  ),
  addServerSavedServers('screen.add_server.saved_servers', 'Saved Servers'),
  addServerFormat('screen.add_server.format', 'Format'),
  addServerToken('screen.add_server.token', 'Token'),
  addServerHiddenUntilDecoded(
    'screen.add_server.hidden_until_decoded',
    'Hidden until decoded',
  ),
  addServerTransport('screen.add_server.transport', 'Transport'),
  addServerDirectHost('screen.add_server.direct_host', 'Direct host'),
  addServerRelayChannel('screen.add_server.relay_channel', 'Relay channel'),
  addServerCheckCode('screen.add_server.check_code', 'Check Code'),
  addServerAwaitingCode('screen.add_server.awaiting_code', 'Awaiting Code'),
  addServerCodeReady('screen.add_server.code_ready', 'Code Ready'),
  addServerStatus('screen.add_server.status', 'Status'),
  addServerWaitingForCode(
    'screen.add_server.waiting_for_code',
    'Waiting for code',
  ),
  addServerExpected('screen.add_server.expected', 'Expected'),
  addServerValidation('screen.add_server.validation', 'Validation'),
  addServerLocalDecode('screen.add_server.local_decode', 'Local decode'),
  addServerOneServer('screen.add_server.one_server', 'One server'),
  addServerHost('screen.add_server.host', 'Host'),
  addServerRelayOnly('screen.add_server.relay_only', 'Relay only'),
  addServerPort('screen.add_server.port', 'Port'),
  addServerFingerprint('screen.add_server.fingerprint', 'Fingerprint'),
  addServerRelay('screen.add_server.relay', 'Relay'),
  addServerNotUsed('screen.add_server.not_used', 'Not used'),
  shellFleetUnavailable('shell.fleet_unavailable', 'Server group unavailable'),
  shellFleetUnavailableDescription(
    'shell.fleet_unavailable_description',
    'The server group has not been initialized yet.',
  ),
  shellServerNotConnected('shell.server_not_connected', 'Server not connected'),
  shellServerNotConnectedDescription(
    'shell.server_not_connected_description',
    'This server is not part of the live server group. Pair it from the sidebar.',
  ),
  shellServersCount('shell.servers_count', 'Server count: {count}', <String>{
    'count',
  }),
  shellWarn('shell.warn', 'Warn'),
  shellSyncing('shell.syncing', 'Syncing'),
  shellStandby('shell.standby', 'Standby'),
  shellPairedCount('shell.paired_count', 'Paired: {count}', <String>{'count'}),
  shellLiveCount('shell.live_count', 'Live: {live}/{total}', <String>{
    'live',
    'total',
  }),
  shellFleetMonitor('shell.fleet_monitor', 'Server Group Monitor'),
  shellState('shell.state', 'State'),
  shellServersLive(
    'shell.servers_live',
    'Live servers: {live}/{total}',
    <String>{'live', 'total'},
  ),
  shellReadyForPairing('shell.ready_for_pairing', 'Ready for pairing'),
  shellRealtimeTelemetry('shell.realtime_telemetry', 'Realtime telemetry'),
  shellPairServer('shell.pair_server', 'Pair Server'),
  shellWorkspace('shell.workspace', 'Workspace'),
  shellFleetControlPlane(
    'shell.fleet_control_plane',
    'Server group control console',
  ),
  shellNoServersConnected('shell.no_servers_connected', 'No servers connected'),
  shellFirstRunDescription(
    'shell.first_run_description',
    'React Web is standing by for authenticated telemetry. Pair a React server to bring TPS, memory, entity pressure, alerts, and optimization controls into this console.',
  ),
  shellFleetSettings('shell.fleet_settings', 'Server Group Settings'),
  shellPairedServers('shell.paired_servers', 'Paired servers'),
  shellSecurePairing('shell.secure_pairing', 'Secure pairing'),
  shellTelemetryStream('shell.telemetry_stream', 'Telemetry stream'),
  shellSignalReadiness('shell.signal_readiness', 'Signal Readiness'),
  shellWaiting('shell.waiting', 'Waiting'),
  shellMemory('shell.memory', 'Memory'),
  shellIncidents('shell.incidents', 'Incidents'),
  shellActions('shell.actions', 'Actions'),
  shellStandbyLower('shell.standby_lower', 'standby'),
  shellLockedLower('shell.locked_lower', 'locked'),
  shellConsoleStatus('shell.console_status', 'Console Status'),
  shellConsoleWaiting(
    'shell.console_waiting',
    'reactor://fleet waiting for first handshake',
  ),
  shellSamplersIdle(
    'shell.samplers_idle',
    'samplers idle until a server is paired',
  ),
  shellAlertsArmed(
    'shell.alerts_armed',
    'alerts queue armed for live telemetry',
  ),
  shellConnectionFlow('shell.connection_flow', 'Connection Flow'),
  shellPair('shell.pair', 'Pair'),
  shellPairDescription('shell.pair_description', 'Add the RCT2 server code.'),
  shellVerify('shell.verify', 'Verify'),
  shellVerifyDescription(
    'shell.verify_description',
    'Confirm the authenticated handshake.',
  ),
  shellMonitor('shell.monitor', 'Monitor'),
  shellMonitorDescription(
    'shell.monitor_description',
    'Open the live server workspace.',
  ),
  shellConnectionLost('shell.connection_lost', 'Connection lost'),
  shellConnectionDegraded('shell.connection_degraded', 'Connection degraded'),
  shellReconnect('shell.reconnect', 'Reconnect'),
  shellServerOffline('shell.server_offline', 'Server offline'),
  shellServerReconnected('shell.server_reconnected', 'Server reconnected'),
  languageSelect('language.select', 'Select language'),
  languageOpen('language.open', 'Open language menu — {language}', <String>{
    'language',
  }),
  languageClose('language.close', 'Close language menu'),
  languageLoadFailed(
    'language.load_failed',
    'The selected language could not be loaded.',
  ),
  commonRetry('common.retry', 'Retry'),
  commonUnavailable('common.unavailable', 'Unavailable'),
  commonActive('common.active', 'Active'),
  commonServer('common.server', 'Server'),
  commonState('common.state', 'State'),
  commonAlerts('common.alerts', 'Alerts'),
  commonLastSeen('common.last_seen', 'Last seen'),
  commonOpenCount('common.open_count', 'Open: {count}', <String>{'count'}),
  commonLinesCount('common.lines_count', 'Line count: {count}', <String>{
    'count',
  }),
  commonLowValue('common.low_value', 'Low {value}', <String>{'value'}),
  commonHighValue('common.high_value', 'High {value}', <String>{'value'}),
  pressureNormal('pressure.normal', 'Normal'),
  pressurePressure('pressure.pressure', 'Pressure'),
  pressurePanic('pressure.panic', 'Panic'),
  shellConnectionNoSnapshotRecovering(
    'shell.connection.no_snapshot_recovering',
    'No telemetry snapshot is available while the live channel recovers.',
  ),
  shellConnectionShowingSnapshotRecovering(
    'shell.connection.showing_snapshot_recovering',
    'Showing the latest received snapshot while the live channel recovers.',
  ),
  shellConnectionNoSnapshotRetrying(
    'shell.connection.no_snapshot_retrying',
    'No telemetry snapshot is available. React will retry automatically.',
  ),
  shellConnectionShowingSnapshotRetrying(
    'shell.connection.showing_snapshot_retrying',
    'Showing the most recent snapshot while React reconnects automatically.',
  ),
  shellNavMonitor('shell.nav.monitor', 'Monitor'),
  shellNavRuntime('shell.nav.runtime', 'Runtime'),
  shellNavAnalyze('shell.nav.analyze', 'Analyze'),
  shellNavControl('shell.nav.control', 'Control'),
  shellNavSystem('shell.nav.system', 'System'),
  shellFleetServerNavigation(
    'shell.a11y.fleet_server_navigation',
    'Server group and individual-server navigation',
  ),
  shellOpenNavigation('shell.a11y.open_navigation', 'Open navigation'),
  shellCloseNavigation('shell.a11y.close_navigation', 'Close navigation'),
  shellOpenInspector('shell.a11y.open_inspector', 'Open inspector'),
  shellCloseInspector('shell.a11y.close_inspector', 'Close inspector'),
  shellCloseSidePanel('shell.a11y.close_side_panel', 'Close side panel'),
  shellFleetInspector('shell.a11y.fleet_inspector', 'Server group inspector'),
  shellServerInspector('shell.a11y.server_inspector', 'Server inspector'),
  shellApplicationStatus('shell.a11y.application_status', 'Application status'),
  shellServerWorkspaceNavigation(
    'shell.a11y.server_workspace_navigation',
    '{server} workspace navigation',
    <String>{'server'},
  ),
  shellActiveServer('shell.inspector.active_server', 'Active server'),
  shellConnection('shell.inspector.connection', 'Connection'),
  shellLastSample('shell.inspector.last_sample', 'Last sample'),
  shellActiveView('shell.inspector.active_view', 'Active view'),
  shellArea('shell.inspector.area', 'Area'),
  shellServerId('shell.inspector.server_id', 'Server ID'),
  shellQuickTelemetry('shell.inspector.quick_telemetry', 'Quick telemetry'),
  shellSnapshot('shell.inspector.snapshot', 'Snapshot'),
  shellNeedsAttention('shell.inspector.needs_attention', 'Needs attention'),
  shellReady('shell.ready', 'Ready'),
  chartNoTimeSeriesData('chart.no_time_series_data', 'No time series data'),
  chartTimeSeriesLabel(
    'chart.time_series_label',
    'Time series chart: {series}',
    <String>{'series'},
  ),
  chartSample('chart.sample', 'Sample {number}', <String>{'number'}),
  chartSampleValues(
    'chart.sample_values',
    'Sample {number}: {values}',
    <String>{'number', 'values'},
  ),
  chartAwaitingSamples('chart.awaiting_samples', 'Awaiting samples'),
  chartAwaitingSamplesDescription(
    'chart.awaiting_samples_description',
    'The chart will populate when the server publishes data.',
  ),
  chartShowSeries('chart.show_series', 'Show {series}', <String>{'series'}),
  chartHideSeries('chart.hide_series', 'Hide {series}', <String>{'series'}),
  heatmapNoScoredChunks(
    'heatmap.no_scored_chunks',
    'No scored chunks were returned for this heatmap.',
  ),
  heatmapChunkTitle('heatmap.chunk_title', 'Chunk {x}, {z} · {score}', <String>{
    'x',
    'z',
    'score',
  }),
  heatmapChunkScore(
    'heatmap.chunk_score',
    'Chunk {x}, {z} score {score}',
    <String>{'x', 'z', 'score'},
  ),
  snapshotWaitingFirst(
    'snapshot.waiting_first',
    'Waiting for the first telemetry snapshot',
  ),
  snapshotNone('snapshot.none', 'No telemetry snapshot'),
  snapshotAfterRecovery(
    'snapshot.after_recovery',
    'This view will populate after the connection recovers and React publishes a snapshot.',
  ),
  snapshotAfterReconnect(
    'snapshot.after_reconnect',
    'This view will populate after React reconnects and publishes a snapshot.',
  ),
  snapshotUnavailable('snapshot.unavailable', 'Telemetry unavailable'),
  snapshotConnectionRequired(
    'snapshot.connection_required',
    'This view requires a server connection.',
  ),
  paneResizeNavigation('pane.resize_navigation', 'Resize server navigation'),
  paneResizeInspector('pane.resize_inspector', 'Resize server inspector'),
  paneWidthPixels('pane.width_pixels', '{width} pixels', <String>{'width'}),
  loadingWaitingLiveData('loading.waiting_live_data', 'Waiting for live data'),
  actionsNoneAvailable('screen.actions.none_available', 'No actions available'),
  actionsNoneAvailableDescription(
    'screen.actions.none_available_description',
    'React did not return any executable operations.',
  ),
  actionsHistoryEmptyDescription(
    'screen.actions.history_empty_description',
    'Executed operations and their tickets will appear here.',
  ),
  actionsUnavailable('screen.actions.unavailable', 'Actions unavailable'),
  alertsNoMatches('screen.alerts.no_matches', 'No alerts match these filters'),
  alertsAdjustFilters(
    'screen.alerts.adjust_filters',
    'Adjust severity or server scope to inspect other alerts.',
  ),
  alertsNoOpenConditions(
    'screen.alerts.no_open_conditions',
    'The server group has no open conditions requiring attention.',
  ),
  comparisonPairServers(
    'screen.comparison.pair_servers',
    'Pair servers to compare telemetry.',
  ),
  comparisonSelectedCount(
    'screen.comparison.selected_count',
    'Selected: {selected}/{total}',
    <String>{'selected', 'total'},
  ),
  comparisonUnavailableCount(
    'screen.comparison.unavailable_count',
    'Unavailable: {count}',
    <String>{'count'},
  ),
  comparisonSelectionWithUnavailable(
    'screen.comparison.selection_with_unavailable',
    'Selected: {selected}/{total} · Unavailable excluded: {unavailable}',
    <String>{'selected', 'total', 'unavailable'},
  ),
  comparisonNoServers('screen.comparison.no_servers', 'No servers available'),
  comparisonNoneSelected(
    'screen.comparison.none_selected',
    'No servers selected',
  ),
  comparisonPairOne(
    'screen.comparison.pair_one',
    'Pair at least one server to open the comparison workspace.',
  ),
  comparisonSelectServers(
    'screen.comparison.select_servers',
    'Select one or more servers from the toolbar above.',
  ),
  comparisonOfflineOrWaiting(
    'screen.comparison.offline_or_waiting',
    'Selected servers are offline or awaiting current telemetry.',
  ),
  comparisonMetricMissing(
    'screen.comparison.metric_missing',
    'The selected metric has not published comparable samples.',
  ),
  comparisonNoSampledMetrics(
    'screen.comparison.no_sampled_metrics',
    'No sampled metrics',
  ),
  comparisonMetricUnavailable(
    'screen.comparison.metric_unavailable',
    '{metric} (unavailable)',
    <String>{'metric'},
  ),
  comparisonServerOffline(
    'screen.comparison.server_offline',
    '{server} · Offline (excluded)',
    <String>{'server'},
  ),
  comparisonServerConnecting(
    'screen.comparison.server_connecting',
    '{server} · Connecting (excluded)',
    <String>{'server'},
  ),
  comparisonServerDegradedEmpty(
    'screen.comparison.server_degraded_empty',
    '{server} · Degraded (no telemetry)',
    <String>{'server'},
  ),
  comparisonServerDegradedCached(
    'screen.comparison.server_degraded_cached',
    '{server} · Degraded (last received)',
    <String>{'server'},
  ),
  comparisonServerAwaiting(
    'screen.comparison.server_awaiting',
    '{server} · Awaiting telemetry (excluded)',
    <String>{'server'},
  ),
  configLiveRequired(
    'screen.config_editor.changes_live_required',
    'Changes require a live server connection.',
  ),
  configApplyingShort('screen.config_editor.applying_short', 'Applying…'),
  configNoSections(
    'screen.config_editor.no_sections',
    'No configuration sections',
  ),
  configNoSectionsDescription(
    'screen.config_editor.no_sections_description',
    'React returned an empty configuration tree.',
  ),
  configUnavailable(
    'screen.config_editor.unavailable',
    'Configuration unavailable',
  ),
  configReload('screen.config_editor.reload', 'Reload'),
  configApplying('screen.config_editor.applying', 'Applying configuration'),
  configApplyingDescription(
    'screen.config_editor.applying_description',
    'Waiting for React to confirm the updated values.',
  ),
  environmentNoValues('screen.environment.no_values', 'No values reported'),
  environmentEmptySection(
    'screen.environment.empty_section',
    'This diagnostic section is empty.',
  ),
  environmentUnavailable(
    'screen.environment.unavailable',
    'Environment diagnostics unavailable',
  ),
  environmentNoRuntimeData(
    'screen.environment.no_runtime_data',
    'React returned no host or runtime diagnostics.',
  ),
  environmentRefreshFailed(
    'screen.environment.refresh_failed',
    'Diagnostic refresh failed',
  ),
  environmentRefreshing(
    'screen.environment.refreshing',
    'Refreshing diagnostics',
  ),
  environmentRefreshingDescription(
    'screen.environment.refreshing_description',
    'Keeping the previous values visible until React replies.',
  ),
  fleetNoFilterMatches(
    'screen.fleet.no_filter_matches',
    'No servers match this filter',
  ),
  fleetChooseAnotherTag(
    'screen.fleet.choose_another_tag',
    'Choose another tag to restore the combined server overview.',
  ),
  fleetNoMatchingServers(
    'screen.fleet.no_matching_servers',
    'No matching servers',
  ),
  fleetFilterEmpty(
    'screen.fleet.filter_empty',
    'The selected server-group filter returned no servers.',
  ),
  fleetNoServersInScope(
    'screen.fleet.no_servers_in_scope',
    'No servers in scope',
  ),
  fleetChooseAnotherFilter(
    'screen.fleet.choose_another_filter',
    'Choose another filter to inspect server-group health.',
  ),
  fleetNoHealthConditions(
    'screen.fleet.no_health_conditions',
    'No open health conditions require operator attention.',
  ),
  fleetAwaitingTelemetry(
    'screen.fleet.awaiting_telemetry',
    'Awaiting telemetry',
  ),
  fleetDegradedAwaiting(
    'screen.fleet.degraded_awaiting',
    'Degraded · awaiting telemetry',
  ),
  governorsRuntimePressure(
    'screen.governors.runtime_pressure',
    'Runtime pressure',
  ),
  governorsNoneAvailable(
    'screen.governors.none_available',
    'No governors available',
  ),
  governorsNoneAvailableDescription(
    'screen.governors.none_available_description',
    'React did not return any adaptive governor features.',
  ),
  governorsLoadingState(
    'screen.governors.loading_state',
    'Loading governor state',
  ),
  governorsUnavailable(
    'screen.governors.unavailable',
    'Governor controls unavailable',
  ),
  heatmapsNoSpatialMetrics(
    'screen.heatmaps.no_spatial_metrics',
    'No spatial metrics',
  ),
  heatmapsNoSpatialMetricsDescription(
    'screen.heatmaps.no_spatial_metrics_description',
    'This snapshot contains no spatial sampler output.',
  ),
  heatmapsEndpointUnavailable(
    'screen.heatmaps.endpoint_unavailable',
    'Heatmap endpoint unavailable',
  ),
  heatmapsRequestFailed(
    'screen.heatmaps.request_failed',
    'Heatmap request failed',
  ),
  heatmapsNoChunkHeatmaps(
    'screen.heatmaps.no_chunk_heatmaps',
    'No chunk heatmaps',
  ),
  heatmapsNoChunkHeatmapsDescription(
    'screen.heatmaps.no_chunk_heatmaps_description',
    'React did not publish any chunk heatmap grids.',
  ),
  incidentCurrentState('screen.incident_center.current_state', 'Current state'),
  incidentNoEvents('screen.incident_center.no_events', 'No incident events'),
  incidentNoEventsDescription(
    'screen.incident_center.no_events_description',
    'React has not recorded an incident timeline.',
  ),
  incidentNoFactors(
    'screen.incident_center.no_factors',
    'No contributing factors',
  ),
  incidentNoFactorsDescription(
    'screen.incident_center.no_factors_description',
    'React did not attribute the current incident score.',
  ),
  incidentUnavailable(
    'screen.incident_center.unavailable',
    'Incident status unavailable',
  ),
  incidentRequestFailed(
    'screen.incident_center.request_failed',
    'Incident request failed',
  ),
  incidentNoStatus('screen.incident_center.no_status', 'No incident status'),
  incidentNoStatusDescription(
    'screen.incident_center.no_status_description',
    'React returned no current incident record.',
  ),
  incidentRefreshFailed(
    'screen.incident_center.refresh_failed',
    'Incident refresh failed',
  ),
  logsAllLevels('screen.logs.all_levels', 'All levels'),
  logsOutputPaused('screen.logs.output_paused', 'Output paused'),
  logsStreamCount(
    'screen.logs.stream_count',
    'Stream line count: {count}',
    <String>{'count'},
  ),
  logsNoLines('screen.logs.no_lines', 'No log lines'),
  logsNoLinesDescription(
    'screen.logs.no_lines_description',
    'New matching entries will appear here as React streams them.',
  ),
  logsOpening('screen.logs.opening', 'Opening the log stream…'),
  logsUnavailable('screen.logs.unavailable', 'Logs unavailable'),
  logsLoadingHistory(
    'screen.logs.loading_history',
    'Loading recent log lines…',
  ),
  logsStreamUnavailable(
    'screen.logs.stream_unavailable',
    'Log stream unavailable',
  ),
  logsRefreshFailed('screen.logs.refresh_failed', 'Log refresh failed'),
  logsRefreshingHistory(
    'screen.logs.refreshing_history',
    'Refreshing log history',
  ),
  logsRefreshingHistoryDescription(
    'screen.logs.refreshing_history_description',
    'Existing streamed lines remain visible during refresh.',
  ),
  logsCommandDisabled(
    'screen.logs.command_disabled',
    'Command execution is disabled until the connection is live.',
  ),
  metricsNonePublished('screen.metrics.none_published', 'No metrics published'),
  metricsNonePublishedDescription(
    'screen.metrics.none_published_description',
    'This server snapshot contains no sampler values.',
  ),
  metricsTryDifferentFilter(
    'screen.metrics.try_different_filter',
    'Try a different sampler name, id, or unit.',
  ),
  optimizationNoneAvailable(
    'screen.optimization.none_available',
    'No optimization features',
  ),
  optimizationNoneAvailableDescription(
    'screen.optimization.none_available_description',
    'React did not return any configurable features.',
  ),
  optimizationUnavailable(
    'screen.optimization.unavailable',
    'Optimization unavailable',
  ),
  settingsRoleEndpointUnavailable(
    'screen.settings.role_endpoint_unavailable',
    'Role endpoint unavailable',
  ),
  settingsEffectiveAccess(
    'screen.settings.effective_access',
    'Effective access for each saved connection.',
  ),
  settingsPairForRole(
    'screen.settings.pair_for_role',
    'Pair a server to inspect its account role.',
  ),
  settingsLoadingRoles('screen.settings.loading_roles', 'Loading roles…'),
  settingsSomeRolesUnavailable(
    'screen.settings.some_roles_unavailable',
    'Some roles are unavailable',
  ),
  settingsRoleLookupFailed(
    'screen.settings.role_lookup_failed',
    'Role lookup failed for {servers}.',
    <String>{'servers'},
  ),
  settingsThresholdDescription(
    'screen.settings.threshold_description',
    'Warning and critical boundaries for the entire server group.',
  ),
  settingsSavedConnectionsCount(
    'screen.settings.saved_connections_count',
    'Saved connection count: {count}',
    <String>{'count'},
  ),
  settingsWaitingForFile(
    'screen.settings.waiting_for_file',
    'Waiting for connection file…',
  ),
  settingsPairToManage(
    'screen.settings.pair_to_manage',
    'Pair a server to manage its label, tags, and credentials.',
  ),
  settingsTags('screen.settings.tags', 'Tags'),
  settingsNoTags('screen.settings.no_tags', 'No tags'),
  settingsRemoveTag('screen.settings.remove_tag', 'Remove tag {tag}', <String>{
    'tag',
  }),
  settingsRelayConnection(
    'screen.settings.relay_connection',
    'Relay connection',
  ),
  tweaksNoneAvailable('screen.tweaks.none_available', 'No tweaks available'),
  tweaksNoneAvailableDescription(
    'screen.tweaks.none_available_description',
    'React did not return any runtime tweaks.',
  ),
  tweaksUnavailable('screen.tweaks.unavailable', 'Tweaks unavailable'),
  worldOverridesUnavailable(
    'screen.world_overrides.unavailable',
    'World overrides unavailable',
  ),
  errorAuthentication('error.authentication', 'Authentication failed.'),
  errorUnavailable('error.unavailable', 'The React server is unavailable.'),
  errorBadRequest('error.bad_request', 'The request was not accepted.'),
  errorForbidden('error.forbidden', 'This account does not have access.'),
  errorNotFound('error.not_found', 'The requested resource was not found.'),
  errorConflict('error.conflict', 'The request conflicts with server state.'),
  errorUnexpected('error.unexpected', 'An unexpected error occurred.');

  final String id;
  final String english;
  final Set<String> placeholders;

  const ReactorText(
    this.id,
    this.english, [
    this.placeholders = const <String>{},
  ]);
}

final class ReactorLocaleSnapshot {
  final Map<ReactorText, String> _messages;

  ReactorLocaleSnapshot._(Map<ReactorText, String> messages)
    : _messages = UnmodifiableMapView<ReactorText, String>(messages);

  factory ReactorLocaleSnapshot.english() =>
      ReactorLocaleSnapshot._(<ReactorText, String>{
        for (final ReactorText key in ReactorText.values) key: key.english,
      });

  String template(ReactorText key) => _messages[key] ?? key.english;
}

final class ReactorOverlayResult {
  final bool applied;
  final int messageCount;
  final String? error;

  const ReactorOverlayResult._({
    required this.applied,
    required this.messageCount,
    this.error,
  });

  const ReactorOverlayResult.applied(int messageCount)
    : this._(applied: true, messageCount: messageCount);

  const ReactorOverlayResult.absent() : this._(applied: false, messageCount: 0);

  const ReactorOverlayResult.rejected(String error)
    : this._(applied: false, messageCount: 0, error: error);
}

final class ReactorLocalizations {
  static final RegExp _placeholderPattern = RegExp(r'\{([a-z][a-zA-Z0-9_]*)\}');

  ReactorLocaleSnapshot _snapshot = ReactorLocaleSnapshot.english();
  Future<ReactorOverlayResult>? _loadFuture;

  ReactorLocaleSnapshot get snapshot => _snapshot;

  String text(
    ReactorText key, [
    Map<String, Object?> arguments = const <String, Object?>{},
  ]) {
    _validateArguments(key, arguments);
    final String template = _snapshot.template(key);
    return template.replaceAllMapped(_placeholderPattern, (Match match) {
      final String name = match.group(1)!;
      return '${arguments[name] ?? ''}';
    });
  }

  ReactorOverlayResult installOverlayJson(String source) {
    return _installJson(source, requireComplete: false);
  }

  ReactorOverlayResult installCompleteCatalogJson(String source) {
    return _installJson(source, requireComplete: true);
  }

  ReactorOverlayResult _installJson(
    String source, {
    required bool requireComplete,
  }) {
    try {
      final Object? decoded = jsonDecode(source);
      if (decoded is! Map<String, Object?>) {
        return const ReactorOverlayResult.rejected(
          'Localization overlay must be a JSON object.',
        );
      }

      final Map<String, ReactorText> keysById = <String, ReactorText>{
        for (final ReactorText key in ReactorText.values) key.id: key,
      };
      if (keysById.length != ReactorText.values.length) {
        return const ReactorOverlayResult.rejected(
          'Localization key IDs must be unique.',
        );
      }
      if (requireComplete && decoded.keys.toSet().length != keysById.length) {
        return const ReactorOverlayResult.rejected(
          'A locale catalog must define every localization key.',
        );
      }

      final Map<ReactorText, String> messages = <ReactorText, String>{
        for (final ReactorText key in ReactorText.values) key: key.english,
      };
      for (final MapEntry<String, Object?> entry in decoded.entries) {
        final ReactorText? key = keysById[entry.key];
        if (key == null) {
          return ReactorOverlayResult.rejected(
            'Unknown localization key: ${entry.key}',
          );
        }
        if (entry.value is! String) {
          return ReactorOverlayResult.rejected(
            'Localization value for ${entry.key} must be a string.',
          );
        }
        final String template = entry.value as String;
        final String? validationError = _validateTemplate(key, template);
        if (validationError != null) {
          return ReactorOverlayResult.rejected(validationError);
        }
        messages[key] = template;
      }

      final ReactorLocaleSnapshot next = ReactorLocaleSnapshot._(messages);
      _snapshot = next;
      return ReactorOverlayResult.applied(decoded.length);
    } on FormatException catch (error) {
      return ReactorOverlayResult.rejected(
        'Localization overlay is invalid JSON: ${error.message}',
      );
    }
  }

  Future<ReactorOverlayResult> loadOverlayOnce(
    FutureOr<String?> Function() loader,
  ) {
    return _loadFuture ??= _loadOverlay(loader);
  }

  Future<ReactorOverlayResult> _loadOverlay(
    FutureOr<String?> Function() loader,
  ) async {
    try {
      final String? source = await loader();
      if (source == null || source.trim().isEmpty) {
        return const ReactorOverlayResult.absent();
      }
      return installOverlayJson(source);
    } catch (error) {
      return ReactorOverlayResult.rejected(
        'Localization overlay could not be loaded: $error',
      );
    }
  }

  static String? validateDefinitions() {
    final Set<String> ids = <String>{};
    for (final ReactorText key in ReactorText.values) {
      if (!ids.add(key.id)) {
        return 'Duplicate localization key ID: ${key.id}';
      }
      final String? validationError = _validateTemplate(key, key.english);
      if (validationError != null) {
        return validationError;
      }
    }
    return null;
  }

  static String? _validateTemplate(ReactorText key, String template) {
    if (template.trim().isEmpty) {
      return 'Localization key ${key.id} must not be empty.';
    }
    final Set<String> found = _extractPlaceholders(template);
    if (!_sameNames(found, key.placeholders)) {
      return 'Localization key ${key.id} must use placeholders '
          '${key.placeholders.toList()..sort()}, found '
          '${found.toList()..sort()}.';
    }

    final String withoutPlaceholders = template.replaceAll(
      _placeholderPattern,
      '',
    );
    if (withoutPlaceholders.contains('{') ||
        withoutPlaceholders.contains('}')) {
      return 'Localization key ${key.id} contains malformed placeholders.';
    }
    return null;
  }

  static Set<String> _extractPlaceholders(String template) =>
      _placeholderPattern
          .allMatches(template)
          .map((Match match) => match.group(1)!)
          .toSet();

  static void _validateArguments(
    ReactorText key,
    Map<String, Object?> arguments,
  ) {
    final Set<String> supplied = arguments.keys.toSet();
    if (!_sameNames(supplied, key.placeholders)) {
      throw ArgumentError.value(
        supplied,
        'arguments',
        'Localization key ${key.id} requires ${key.placeholders}.',
      );
    }
  }

  static bool _sameNames(Set<String> left, Set<String> right) =>
      left.length == right.length && left.containsAll(right);
}

final ReactorLocalizations reactorLocalizations = ReactorLocalizations();

String reactorText(
  ReactorText key, [
  Map<String, Object?> arguments = const <String, Object?>{},
]) => reactorLocalizations.text(key, arguments);
