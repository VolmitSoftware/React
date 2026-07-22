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
  appTitle('app.title', 'Reactor'),
  appDescription('app.description', 'React plugin monitoring dashboard'),
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
    '{enabled} / {total} enabled',
    <String>{'enabled', 'total'},
  ),
  optimizationEnableAll('screen.optimization.enable_all', 'Enable all'),
  optimizationDisableAll('screen.optimization.disable_all', 'Disable all'),
  optimizationCategoryCount(
    'screen.optimization.category_count',
    '{enabled} of {total} on',
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
  fleetTitle('screen.fleet.title', 'Fleet'),
  fleetNoServersPaired('screen.fleet.no_servers_paired', 'No servers paired'),
  fleetAllServersNominal(
    'screen.fleet.all_servers_nominal',
    'All {count} servers nominal',
    <String>{'count'},
  ),
  fleetServersNeedAttention(
    'screen.fleet.servers_need_attention',
    '{total} servers · {attention} need attention',
    <String>{'total', 'attention'},
  ),
  fleetHealth('screen.fleet.health', 'Fleet Health'),
  fleetTag('screen.fleet.tag', 'Tag'),
  fleetMeanTps('screen.fleet.mean_tps', 'Mean TPS'),
  fleetWorstTps('screen.fleet.worst_tps', 'Worst TPS'),
  fleetCompositeHealth('screen.fleet.composite_health', 'Composite Health'),
  fleetTotalPlayers('screen.fleet.total_players', 'Total Players'),
  fleetWorstMspt('screen.fleet.worst_mspt', 'Worst MSPT'),
  fleetCriticalCount(
    'screen.fleet.critical_count',
    '{count} critical',
    <String>{'count'},
  ),
  fleetWarningCount('screen.fleet.warning_count', '{count} warning', <String>{
    'count',
  }),
  fleetInfoCount('screen.fleet.info_count', '{count} info', <String>{'count'}),
  fleetServers('screen.fleet.servers', 'Servers'),
  fleetPairedCount('screen.fleet.paired_count', '{count} paired', <String>{
    'count',
  }),
  fleetNeedsAttention('screen.fleet.needs_attention', 'Needs Attention'),
  fleetAllHealthy('screen.fleet.all_healthy', 'All servers healthy'),
  fleetAlerts('screen.fleet.alerts', 'Alerts'),
  fleetLastSeen('screen.fleet.last_seen', 'Last Seen'),
  fleetOpenDashboard('screen.fleet.open_dashboard', 'Open dashboard'),
  fleetAlertCount('screen.fleet.alert_count', 'Open alerts: {count}', <String>{
    'count',
  }),
  alertsTitle('screen.alerts.title', 'Alerts'),
  alertsSubtitle('screen.alerts.subtitle', 'Open fleet alerts'),
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
    'Alert thresholds and server tags',
  ),
  settingsThresholdsSaved(
    'screen.settings.thresholds_saved',
    'Thresholds saved',
  ),
  settingsFleetCleared('screen.settings.fleet_cleared', 'Saved fleet cleared'),
  settingsNothingToExport(
    'screen.settings.nothing_to_export',
    'Nothing to export',
  ),
  settingsNoServersConfigured(
    'screen.settings.no_servers_configured',
    'No servers configured.',
  ),
  settingsFleetImported('screen.settings.fleet_imported', 'Fleet imported'),
  settingsServersLoaded(
    'screen.settings.servers_loaded',
    'Servers loaded: {count}.',
    <String>{'count'},
  ),
  settingsFleetUnavailable(
    'screen.settings.fleet_unavailable',
    'Fleet unavailable',
  ),
  settingsFleetNotInitialized(
    'screen.settings.fleet_not_initialized',
    'No fleet has been initialized.',
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
    'Import failed: {error}',
    <String>{'error'},
  ),
  settingsReplaceFleet('screen.settings.replace_fleet', 'Replace fleet?'),
  settingsReplaceFleetMessage(
    'screen.settings.replace_fleet_message',
    'This will replace the current fleet ({current}) with the servers from the file ({incoming}).',
    <String>{'current', 'incoming'},
  ),
  settingsMalformedSkipped(
    'screen.settings.malformed_skipped',
    ' Malformed entries to skip: {count}.',
    <String>{'count'},
  ),
  fleetImportInvalidJson('fleet_import.invalid_json', 'Invalid JSON'),
  fleetImportInvalidFile(
    'fleet_import.invalid_file',
    'Not a valid fleet export file',
  ),
  fleetImportWrongKind(
    'fleet_import.wrong_kind',
    'Not a reactor-fleet export file',
  ),
  fleetImportInvalidServerList(
    'fleet_import.invalid_server_list',
    'Missing or invalid servers list',
  ),
  fleetImportNoValidServer(
    'fleet_import.no_valid_server',
    'No valid servers found ({count} malformed entry)',
    <String>{'count'},
  ),
  fleetImportNoValidServers(
    'fleet_import.no_valid_servers',
    'No valid servers found ({count} malformed entries)',
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
    'This will disconnect and remove the server from your fleet.',
  ),
  settingsAddTag('screen.settings.add_tag', 'Add tag'),
  addServerTitle('screen.add_server.title', 'Add Server'),
  addServerSubtitle(
    'screen.add_server.subtitle',
    'Paste an authenticated RCT1 code from the React server console',
  ),
  addServerPasteFullCode(
    'screen.add_server.paste_full_code',
    'Paste the full RCT1 pairing code.',
  ),
  addServerPrefixRequired(
    'screen.add_server.prefix_required',
    'Pairing codes must start with RCT1.',
  ),
  addServerPayloadMissing(
    'screen.add_server.payload_missing',
    'The RCT1 payload is missing.',
  ),
  addServerCodeIncomplete(
    'screen.add_server.code_incomplete',
    'This code is incomplete. Copy the entire Pairing code line from the server console.',
  ),
  addServerDecodeFailed(
    'screen.add_server.decode_failed',
    'This RCT1 code could not be decoded. Copy the full code without truncating it.',
  ),
  addServerFleetClearedMessage(
    'screen.add_server.fleet_cleared_message',
    'Saved fleet cleared. Paste a new RCT1 code to reconnect.',
  ),
  addServerFleetReset('screen.add_server.fleet_reset', 'Fleet reset'),
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
    'Invalid pairing code. Copy the full RCT1 line from the server console.',
  ),
  addServerInvalidPairingDescription(
    'screen.add_server.invalid_pairing_description',
    'Check that you copied the full RCT1 code from the server console.',
  ),
  addServerConnectionFailedMessage(
    'screen.add_server.connection_failed_message',
    'Could not connect to the server API. Verify the host, port, token, and that the web controller is reachable.',
  ),
  addServerPairingFailed('screen.add_server.pairing_failed', 'Pairing failed'),
  addServerClearCode('screen.add_server.clear_code', 'Clear code'),
  addServerConfirmReset('screen.add_server.confirm_reset', 'Confirm reset'),
  addServerResetFleet('screen.add_server.reset_fleet', 'Reset fleet'),
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
  addServerHandshake('screen.add_server.handshake', 'RCT1 handshake'),
  addServerNeedsFullCode(
    'screen.add_server.needs_full_code',
    'Needs full code',
  ),
  addServerStandby('screen.add_server.standby', 'Standby'),
  addServerDecoded('screen.add_server.decoded', 'Decoded'),
  addServerInputPlaceholder(
    'screen.add_server.input_placeholder',
    'Paste RCT1. code from server console',
  ),
  addServerInputHelper(
    'screen.add_server.input_helper',
    'You can paste the raw RCT1 token or the full console line.',
  ),
  addServerConnectionFlow(
    'screen.add_server.connection_flow',
    'Connection Flow',
  ),
  addServerConnectionFlowDescription(
    'screen.add_server.connection_flow_description',
    'The dashboard validates locally before opening telemetry.',
  ),
  addServerCopy('screen.add_server.copy', 'Copy'),
  addServerCopyDescription(
    'screen.add_server.copy_description',
    'Run the React pairing command and copy the full RCT1 value.',
  ),
  addServerDecode('screen.add_server.decode', 'Decode'),
  addServerDecodeDescription(
    'screen.add_server.decode_description',
    'Reactor checks the transport, token, and confirmation word.',
  ),
  addServerMonitor('screen.add_server.monitor', 'Monitor'),
  addServerMonitorDescription(
    'screen.add_server.monitor_description',
    'A live server workspace opens as soon as the fleet accepts it.',
  ),
  addServerSecurity('screen.add_server.security', 'Security'),
  addServerSecurityDescription(
    'screen.add_server.security_description',
    'Pairing credentials stay in browser storage for this console.',
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
  addServerConfirmWord('screen.add_server.confirm_word', 'Confirm Word'),
  addServerRelay('screen.add_server.relay', 'Relay'),
  addServerNotUsed('screen.add_server.not_used', 'Not used'),
  shellFleetUnavailable('shell.fleet_unavailable', 'Fleet unavailable'),
  shellFleetUnavailableDescription(
    'shell.fleet_unavailable_description',
    'The server fleet has not been initialized yet.',
  ),
  shellServerNotConnected('shell.server_not_connected', 'Server not connected'),
  shellServerNotConnectedDescription(
    'shell.server_not_connected_description',
    'This server is not part of the live fleet. Pair it from the sidebar.',
  ),
  shellServerCount('shell.server_count', 'Server ({count})', <String>{'count'}),
  shellServersCount('shell.servers_count', 'Servers ({count})', <String>{
    'count',
  }),
  shellWarn('shell.warn', 'Warn'),
  shellSyncing('shell.syncing', 'Syncing'),
  shellStandby('shell.standby', 'Standby'),
  shellPairedCount('shell.paired_count', '{count} paired', <String>{'count'}),
  shellLiveCount('shell.live_count', '{live}/{total} live', <String>{
    'live',
    'total',
  }),
  shellFleetMonitor('shell.fleet_monitor', 'Fleet Monitor'),
  shellState('shell.state', 'State'),
  shellServersLive(
    'shell.servers_live',
    '{live}/{total} servers live',
    <String>{'live', 'total'},
  ),
  shellReadyForPairing('shell.ready_for_pairing', 'Ready for pairing'),
  shellRealtimeTelemetry('shell.realtime_telemetry', 'Realtime telemetry'),
  shellPairServer('shell.pair_server', 'Pair Server'),
  shellWorkspace('shell.workspace', 'Workspace'),
  shellFleetControlPlane('shell.fleet_control_plane', 'Fleet control plane'),
  shellNoServersConnected('shell.no_servers_connected', 'No servers connected'),
  shellFirstRunDescription(
    'shell.first_run_description',
    'Reactor is standing by for authenticated telemetry. Pair a React server to bring TPS, memory, entity pressure, alerts, and optimization controls into this console.',
  ),
  shellFleetSettings('shell.fleet_settings', 'Fleet Settings'),
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
  shellPairDescription('shell.pair_description', 'Add the RCT1 server code.'),
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
  shellServerReconnected('shell.server_reconnected', 'Server reconnected');

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
    try {
      final Object? decoded = jsonDecode(source);
      if (decoded is! Map<String, dynamic>) {
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

      final Map<ReactorText, String> messages = <ReactorText, String>{
        for (final ReactorText key in ReactorText.values) key: key.english,
      };
      for (final MapEntry<String, dynamic> entry in decoded.entries) {
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
