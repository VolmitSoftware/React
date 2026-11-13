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
  statusLive('status.live', 'Online'),
  statusElevated('status.elevated', 'Elevated'),
  statusCritical('status.critical', 'Critical'),
  commonPlayers('common.players', 'Players'),
  commonEntities('common.entities', 'Entities'),
  commonChunks('common.chunks', 'Chunks'),
  commonTickTime('common.tick_time', 'Tick time'),
  commonIncidentScore('common.incident_score', 'Incident score'),
  commonMemoryUsed('common.memory_used', 'Memory used'),
  commonGcTime('common.gc_time', 'GC time'),
  commonTopWorldMspt('common.top_world_mspt', 'Top world MSPT'),
  commonTopChunkCost('common.top_chunk_cost', 'Top chunk cost'),
  commonQueue('common.queue', 'Queue'),
  commonQueueTime('common.queue_time', 'Queue time'),
  commonBudget('common.budget', 'Budget'),
  commonProcessLoad('common.process_load', 'Process load'),
  commonEventTime('common.event_time', 'Event time'),
  commonPingP95('common.ping_p95', 'Ping p95'),
  commonRedstone('common.redstone', 'Redstone'),
  commonHoppers('common.hoppers', 'Hoppers'),
  commonPhysics('common.physics', 'Physics'),
  commonFluid('common.fluid', 'Fluid'),
  commonOptimizations('common.optimizations', 'Optimizations'),
  configNoOptions('config.no_options', 'No configurable options'),
  chunksTitle('screen.chunks.title', 'Chunks'),
  chunksListenerTime('screen.chunks.listener_time', 'Chunk listener time'),
  chunksLoadedPerSecond('screen.chunks.loaded_per_second', 'Loaded'),
  chunksGeneratedPerSecond('screen.chunks.generated_per_second', 'Generated'),
  chunksLoadListenerTime(
    'screen.chunks.load_listener_time',
    'Load listener time',
  ),
  chunksGenListenerTime('screen.chunks.gen_listener_time', 'Gen listener time'),
  chunksPersistence('screen.chunks.persistence', 'Persistence'),
  chunksWorldSaveEventInterval(
    'screen.chunks.world_save_event_interval',
    'Save interval',
  ),
  chunksResidency('screen.chunks.residency', 'Residency'),
  chunksPdcBatcher('screen.chunks.pdc_batcher', 'PDC writes'),
  entitiesTitle('screen.entities.title', 'Entities'),
  entitiesCount('screen.entities.count', 'Entity count'),
  entitiesPlayerPing('screen.entities.player_ping', 'Player ping'),
  entitiesAiActive('screen.entities.ai_active', 'AI active'),
  entitiesSpawnsPerSecond('screen.entities.spawns_per_second', 'Spawns'),
  entitiesPingJitter('screen.entities.ping_jitter', 'Ping jitter'),
  entitiesBreakdown('screen.entities.breakdown', 'Entity breakdown'),
  entitiesPlayerActivity('screen.entities.player_activity', 'Player activity'),
  eventsTitle('screen.events.title', 'Events'),
  eventsHandlesPerTick('screen.events.handles_per_tick', 'Handles/tick'),
  eventsListeners('screen.events.listeners', 'Listeners'),
  worldsTitle('screen.worlds.title', 'Worlds'),
  worldsSubtitle('screen.worlds.subtitle', 'Per-world performance'),
  performanceTitle('screen.performance.title', 'Performance'),
  performanceSubtitle(
    'screen.performance.subtitle',
    'Tick timing and load hotspots',
  ),
  performanceSpikeRate('screen.performance.spike_rate', 'Spike rate'),
  metricsTitle('screen.metrics.title', 'Metrics'),
  metricsSubtitle(
    'screen.metrics.subtitle',
    'Every sampler currently published by this React server',
  ),
  metricsCatalog('screen.metrics.catalog', 'Live sampler catalog'),
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
  metricsNoMatches(
    'screen.metrics.no_matches',
    'No samplers match this filter.',
  ),
  metricsSampler('screen.metrics.sampler', 'Sampler'),
  metricsCurrent('screen.metrics.current', 'Current'),
  metricsMinimum('screen.metrics.minimum', 'Minimum'),
  metricsMaximum('screen.metrics.maximum', 'Maximum'),
  metricsSamples('screen.metrics.samples', 'Samples'),
  metricsHistory('screen.metrics.history', 'Historical data'),
  metricsHistoryDescription(
    'screen.metrics.history_description',
    'Select a sampler and time range. Only the visible range is loaded from disk.',
  ),
  metricsSelectHistory(
    'screen.metrics.select_history',
    'Select a sampler to inspect its stored history.',
  ),
  metricsLoadingHistory(
    'screen.metrics.loading_history',
    'Loading historical data…',
  ),
  metricsHistoryUnavailable(
    'screen.metrics.history_unavailable',
    'Historical data is unavailable for this server.',
  ),
  metricsAverage('screen.metrics.average', 'Average'),
  metricsLast('screen.metrics.last', 'Last'),
  metricsResolution(
    'screen.metrics.resolution',
    'Resolution: {resolution}',
    <String>{'resolution'},
  ),
  metricsPointCount('screen.metrics.point_count', '{count} points', <String>{
    'count',
  }),
  metricsStoredCoverage(
    'screen.metrics.stored_coverage',
    'Stored {from} – {to}',
    <String>{'from', 'to'},
  ),
  metricsActive('screen.metrics.active', 'Active'),
  metricsDormant('screen.metrics.dormant', 'Historical'),
  memoryTitle('screen.memory.title', 'Memory'),
  memoryUsed('screen.memory.used', 'Used'),
  memoryFree('screen.memory.free', 'Free'),
  memoryAfterGc('screen.memory.after_gc', 'After GC'),
  memoryPressure('screen.memory.pressure', 'Memory pressure'),
  memoryGcPauseP95('screen.memory.gc_pause_p95', 'GC pause p95'),
  memoryHeapUsage('screen.memory.heap_usage', 'Heap usage'),
  memoryAllocation('screen.memory.allocation', 'Allocation'),
  internalsTitle('screen.internals.title', 'Internals'),
  internalsAsync('screen.internals.async', 'Async'),
  internalsSync('screen.internals.sync', 'Sync'),
  internalsReactTickTime('screen.internals.react_tick_time', 'React tick time'),
  internalsJobs('screen.internals.jobs', 'Jobs'),
  internalsCpuLoad('screen.internals.cpu_load', 'CPU load'),
  internalsSystemLoad('screen.internals.system_load', 'System load'),
  internalsOutsideLoad('screen.internals.outside_load', 'Outside load'),
  internalsTelemetryPipeline(
    'screen.internals.telemetry_pipeline',
    'Telemetry pipeline',
  ),
  internalsWebTelemetry('screen.internals.web_telemetry', 'Web telemetry'),
  internalsJvmRuntime('screen.internals.jvm_runtime', 'JVM runtime'),
  overviewTitle('screen.overview.title', 'Overview'),
  overviewSubtitle(
    'screen.overview.subtitle',
    'Live server health and key runtime telemetry',
  ),
  overviewTps('screen.overview.tps', 'TPS'),
  mechanicsTitle('screen.mechanics.title', 'Mechanics'),
  mechanicsBurstRate('screen.mechanics.burst_rate', 'Burst rate'),
  mechanicsChainCoalescing(
    'screen.mechanics.chain_coalescing',
    'Chain coalescing',
  ),
  mechanicsPhysicsFluids(
    'screen.mechanics.physics_fluids',
    'Physics and fluids',
  ),
  mechanicsRedstoneEventSpan(
    'screen.mechanics.redstone_event_span',
    'Redstone event span',
  ),
  mechanicsHopperEventSpan(
    'screen.mechanics.hopper_event_span',
    'Hopper event span',
  ),
  mechanicsPhysicsEventSpan(
    'screen.mechanics.physics_event_span',
    'Physics event span',
  ),
  mechanicsFluidEventSpan(
    'screen.mechanics.fluid_event_span',
    'Fluid event span',
  ),
  mechanicsUpdates('screen.mechanics.updates', 'Updates'),
  mechanicsCropFastForward(
    'screen.mechanics.crop_fast_forward',
    'Crop fast-forward',
  ),
  mechanicsLazyGravitySkipped(
    'screen.mechanics.lazy_gravity_skipped',
    'Gravity skips',
  ),
  mechanicsSpawnerLightCacheSkipped(
    'screen.mechanics.spawner_light_cache_skipped',
    'Light cache skips',
  ),
  mechanicsExplosionPacketReduction(
    'screen.mechanics.explosion_packet_reduction',
    'Explosion packets',
  ),
  incidentsTitle('screen.incidents.title', 'Incidents'),
  incidentsSubtitle(
    'screen.incidents.subtitle',
    'Incident scoring and history',
  ),
  incidentsBacklog('screen.incidents.backlog', 'Backlog'),
  incidentsBacklogGrowth('screen.incidents.backlog_growth', 'Backlog growth'),
  incidentsScore('screen.incidents.score', 'Score'),
  incidentsLatest('screen.incidents.latest', 'Latest incidents'),
  incidentsUpdated('screen.incidents.updated', 'Updated {time}', <String>{
    'time',
  }),
  incidentsContributors(
    'screen.incidents.contributing_factors',
    'Contributing factors',
  ),
  incidentsLiveRequired(
    'screen.incidents.live_required',
    'Incident data requires a live connection.',
  ),
  incidentsLoading('screen.incidents.loading', 'Loading incidents...'),
  incidentsNoDominantCause(
    'screen.incidents.no_dominant_cause',
    'No measured factor is currently contributing score points.',
  ),
  incidentsScoreUnavailable(
    'screen.incidents.score_unavailable',
    'The incident score inputs are currently unavailable.',
  ),
  incidentsPrimaryCause(
    'screen.incidents.primary_cause_value',
    '{metric} at {value} contributed {points} score points.',
    <String>{'metric', 'value', 'points'},
  ),
  incidentsScorePoints('screen.incidents.score_points', 'points'),
  incidentsLocation('screen.incidents.location', 'Location'),
  incidentsActions('screen.incidents.actions', 'Actions'),
  incidentsEvidence('screen.incidents.evidence', 'Evidence'),
  incidentsDetails('screen.incidents.details', 'Details'),
  incidentsResolved('screen.incidents.resolved', 'Resolved'),
  incidentsNoEvents('screen.incidents.no_events', 'No incident events'),
  incidentsNoFactors('screen.incidents.no_factors', 'No contributing factors'),
  incidentsRequestFailed(
    'screen.incidents.request_failed',
    'Incident request failed',
  ),
  incidentsRefreshFailed(
    'screen.incidents.refresh_failed',
    'Incident refresh failed',
  ),
  commonUpdateFailed('common.update_failed', 'Update failed'),
  commonRequiresAdminRole('common.requires_admin_role', 'Requires admin role'),
  commonEnabled('common.enabled', 'Enabled'),
  commonDisabled('common.disabled', 'Disabled'),
  commonLoadingWorlds('common.loading_worlds', 'Loading worlds...'),
  logsSubtitle('screen.logs.subtitle', 'Live server log stream'),
  logsResume('screen.logs.resume', 'Resume'),
  logsPause('screen.logs.pause', 'Pause'),
  logsClear('screen.logs.clear', 'Clear'),
  logsLevel('screen.logs.level', 'Level'),
  logsLevelWarn('screen.logs.level_warn', 'Warnings'),
  logsLevelError('screen.logs.level_error', 'Errors'),
  logsLiveRequired(
    'screen.logs.live_required',
    'Logs require a live connection.',
  ),
  consoleRun('screen.logs.console.run', 'Run'),
  consoleRejected(
    'screen.logs.console.rejected',
    'The server rejected the command.',
  ),
  consoleFailed('screen.logs.console.failed', 'Command failed'),
  integrationsTitle('screen.integrations.title', 'Integrations'),
  integrationsNone('screen.integrations.none', 'No integrations detected'),
  integrationsNoneDescription(
    'screen.integrations.none_description',
    'No integration metrics are being reported by this server.',
  ),
  pluginApiSubtitle(
    'screen.plugin_api.subtitle',
    'Community-defined metrics collected from installed plugins',
  ),
  pluginApiRefresh('screen.plugin_api.refresh', 'Refresh'),
  pluginApiNew('screen.plugin_api.new', 'New pack'),
  pluginApiFolder('screen.plugin_api.folder', 'Server folder'),
  pluginApiCatalog('screen.plugin_api.catalog', 'Installed packs'),
  pluginApiNone('screen.plugin_api.none', 'No packs installed'),
  pluginApiContent('screen.plugin_api.content', 'TOML content'),
  pluginApiValidate('screen.plugin_api.validate', 'Validate'),
  pluginApiInstall('screen.plugin_api.install', 'Install pack'),
  pluginApiDelete('screen.plugin_api.delete', 'Delete'),
  pluginApiEdit('screen.plugin_api.edit', 'Edit'),
  pluginApiTrusted('screen.plugin_api.trusted', 'Trusted'),
  pluginApiTarget('screen.plugin_api.target', 'Target'),
  pluginApiMetrics('screen.plugin_api.metrics', 'Metrics'),
  pluginApiAvailable('screen.plugin_api.available', 'Available'),
  pluginApiUnavailable('screen.plugin_api.unavailable', 'Unavailable'),
  pluginApiLoading('screen.plugin_api.loading', 'Loading Plugin API packs…'),
  pluginApiLoadFailed(
    'screen.plugin_api.load_failed',
    'Could not load Plugin API packs',
  ),
  pluginApiValid('screen.plugin_api.valid', 'Pack is valid'),
  pluginApiInvalid('screen.plugin_api.invalid', 'Pack is invalid'),
  pluginApiInstalled('screen.plugin_api.installed', 'Pack installed'),
  pluginApiRemoved('screen.plugin_api.removed', 'Pack removed'),
  pluginApiLiveRequired(
    'screen.plugin_api.live_required',
    'Pack changes require a live server connection.',
  ),
  heatmapsSubtitle('screen.heatmaps.subtitle', 'Spatial load distribution'),
  heatmapsEntityPressure('screen.heatmaps.entity_pressure', 'Entity pressure'),
  heatmapsChunkLoadGenCost(
    'screen.heatmaps.chunk_load_gen_cost',
    'Chunk load/gen cost',
  ),
  heatmapsChunkSampler('screen.heatmaps.chunk_sampler', 'Sampled chunks'),
  heatmapsRedstoneActivity(
    'screen.heatmaps.redstone_activity',
    'Redstone activity',
  ),
  heatmapsHopperThroughput(
    'screen.heatmaps.hopper_throughput',
    'Hopper throughput',
  ),
  heatmapsTickSpikeOrigin(
    'screen.heatmaps.tick_spike_origin',
    'Tick spike origin',
  ),
  heatmapsEventImpactPie(
    'screen.heatmaps.event_impact_pie',
    'Event cost share',
  ),
  heatmapsEventImpactList(
    'screen.heatmaps.event_impact_list',
    'Plugins sampled',
  ),
  heatmapsIrisBiomeShare(
    'screen.heatmaps.iris_biome_share',
    'Iris biome share',
  ),
  heatmapsIrisWorldShare(
    'screen.heatmaps.iris_world_share',
    'Iris world share',
  ),
  heatmapsSpatialMetrics('screen.heatmaps.spatial_metrics', 'Spatial metrics'),
  heatmapsChunkHeatmaps('screen.heatmaps.chunk_heatmaps', 'Chunk heatmaps'),
  heatmapsLoading('screen.heatmaps.loading', 'Loading heatmaps...'),
  heatmapsWorld('screen.heatmaps.world', 'World'),
  heatmapsCenterChunkX('screen.heatmaps.center_chunk_x', 'Center chunk X'),
  heatmapsCenterChunkZ('screen.heatmaps.center_chunk_z', 'Center chunk Z'),
  heatmapsRadiusChunks('screen.heatmaps.radius_chunks', 'Radius (chunks)'),
  heatmapsZoomIn('screen.heatmaps.zoom_in', 'Zoom in'),
  heatmapsZoomOut('screen.heatmaps.zoom_out', 'Zoom out'),
  heatmapsCenterSpawn('screen.heatmaps.center_spawn', 'Center spawn'),
  heatmapsFitBorder('screen.heatmaps.fit_border', 'Fit border'),
  heatmapsLiveRequired(
    'screen.heatmaps.live_required',
    'Grid heatmaps require a live connection.',
  ),
  worldOverridesTitle('screen.world_overrides.title', 'World overrides'),
  worldOverridesNoWorlds('screen.world_overrides.no_worlds', 'No worlds'),
  worldOverridesBudgetMs('screen.world_overrides.budget_ms', 'Budget (ms)'),
  worldOverridesPanicMs('screen.world_overrides.panic_ms', 'Panic (ms)'),
  worldOverridesReleaseMs('screen.world_overrides.release_ms', 'Release (ms)'),
  worldOverridesLiveRequired(
    'screen.world_overrides.live_required',
    'Per-world overrides require a live connection.',
  ),
  governorsTitle('screen.governors.title', 'Governors'),
  tweaksTitle('screen.tweaks.title', 'Tweaks'),
  tweaksLiveRequired(
    'screen.tweaks.live_required',
    'Tweak control requires a live connection.',
  ),
  tweaksLoading('screen.tweaks.loading', 'Loading tweaks...'),
  actionsTitle('screen.actions.title', 'Actions'),
  actionsSubtitle('screen.actions.subtitle', 'Operational commands'),
  actionsExecute('screen.actions.execute', 'Run'),
  actionsDestructive('screen.actions.destructive', 'Destructive'),
  actionsNoneExecuted('screen.actions.none_executed', 'No runs yet'),
  actionsQueued('screen.actions.queued', 'Action queued'),
  actionsFailed('screen.actions.failed', 'Action failed'),
  actionsLiveRequired(
    'screen.actions.live_required',
    'Actions require a live connection.',
  ),
  actionsLoading('screen.actions.loading', 'Loading actions...'),
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
  optimizationEnableAll('screen.optimization.enable_all', 'Enable all'),
  optimizationDisableAll('screen.optimization.disable_all', 'Disable all'),
  optimizationConfigure('screen.optimization.configure', 'Configure'),
  optimizationLiveRequired(
    'screen.optimization.live_required',
    'Feature control requires a live connection.',
  ),
  optimizationLoading('screen.optimization.loading', 'Loading features...'),
  comparisonNoData('screen.comparison.no_data', 'No data for selected metric'),
  comparisonLeaderboard('screen.comparison.leaderboard', 'Leaderboard'),
  comparisonMetric('screen.comparison.metric', 'Metric'),
  environmentLiveRequired(
    'screen.environment.live_required',
    'Environment data requires a live connection.',
  ),
  environmentLoading('screen.environment.loading', 'Loading diagnostics...'),
  environmentNoData(
    'screen.environment.no_data',
    'No environment data available.',
  ),
  commonAll('common.all', 'All'),
  commonHealthy('common.healthy', 'Healthy'),
  commonWarning('common.warning', 'Warning'),
  commonNever('common.never', 'Never'),
  commonSecondsAgo('common.seconds_ago', '{value}s ago', <String>{'value'}),
  commonMinutesAgo('common.minutes_ago', '{value}m ago', <String>{'value'}),
  commonHoursAgo('common.hours_ago', '{value}h ago', <String>{'value'}),
  commonDaysAgo('common.days_ago', '{value}d ago', <String>{'value'}),
  fleetNoServersPaired('screen.fleet.no_servers_paired', 'No servers paired'),
  fleetTag('screen.fleet.tag', 'Tag'),
  fleetWorstTps('screen.fleet.worst_tps', 'Worst TPS'),
  fleetWorstMspt('screen.fleet.worst_mspt', 'Worst tick time'),
  fleetServers('screen.fleet.servers', 'Servers'),
  fleetNeedsAttention('screen.fleet.needs_attention', 'Needs attention'),
  alertsTitle('screen.alerts.title', 'Alerts'),
  alertsSubtitle('screen.alerts.subtitle', 'Open server-group alerts'),
  alertsNoneOpen('screen.alerts.none_open', 'No open alerts'),
  alertsSeverityCritical('screen.alerts.severity.critical', 'Critical'),
  alertsSeverityWarning('screen.alerts.severity.warning', 'Warning'),
  alertsSeverityInfo('screen.alerts.severity.info', 'Info'),
  alertsAck('screen.alerts.ack', 'Acknowledge'),
  alertsAcked('screen.alerts.acked', 'Acknowledged'),
  alertsResolve('screen.alerts.resolve', 'Clear'),
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
  settingsDarkTheme('screen.settings.dark_theme', 'Dark'),
  settingsLightTheme('screen.settings.light_theme', 'Light'),
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
  settingsAlertThresholds(
    'screen.settings.alert_thresholds',
    'Alert thresholds',
  ),
  settingsTpsWarn('screen.settings.tps_warn', 'TPS warning'),
  settingsTpsCritical('screen.settings.tps_critical', 'TPS critical'),
  settingsMsptWarn('screen.settings.mspt_warn', 'Tick time warning'),
  settingsIncidentScoreWarn(
    'screen.settings.incident_score_warn',
    'Incident score warning',
  ),
  settingsGcPercentWarn('screen.settings.gc_percent_warn', 'GC time warning'),
  settingsPingP95Warn('screen.settings.ping_p95_warn', 'Ping p95 warning'),
  settingsMemoryPressureWarn(
    'screen.settings.memory_pressure_warn',
    'Memory pressure warning',
  ),
  settingsResetDefaults('screen.settings.reset_defaults', 'Reset to defaults'),
  settingsSavedServers('screen.settings.saved_servers', 'Saved servers'),
  settingsClearAll('screen.settings.clear_all', 'Remove all'),
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
  pageHomeTitle('page.home.title', 'Home'),
  startContinue('start.continue', 'Continue'),
  startOnline('start.online', 'Online'),
  startChecking('start.checking', 'Checking'),
  startNotReachable('start.not_reachable', 'Not reachable'),
  startOpen('start.open', 'Open'),
  startOpenServer('start.open_server', 'Open {server}', <String>{'server'}),
  startForget('start.forget', 'Forget server'),
  startForgetTitle('start.forget_title', 'Forget {server}?', <String>{
    'server',
  }),
  startForgetMessage(
    'start.forget_message',
    'Removes it from this browser. The server accepts its pairing until it ends; /react web revoke {token} ends it now.',
    <String>{'token'},
  ),
  startForgot('start.forgot', 'Forgot {server}.', <String>{'server'}),
  pairTitle('pair.title', 'Pair a server'),
  pairLink('pair.link', 'Pairing link'),
  pairNewLink('pair.new_link', 'New link'),
  pairPair('pair.pair', 'Pair'),
  pairPasteLink('pair.paste_link', 'Paste the link from the console or chat.'),
  pairCommand('pair.command', 'Run on the server'),
  pairCommandTip(
    'pair.command_tip',
    'In game or in the console. It prints a link that works once, within 10 minutes. role=viewer or role=admin changes what it allows.',
  ),
  pairCopyCommand('pair.copy_command', 'Copy command'),
  pairCopied('pair.copied', 'Command copied'),
  pairCopyFailed('pair.copy_failed', 'Could not copy'),
  pairLinkUntil('pair.link_until', 'Link works until {time}, once.', <String>{
    'time',
  }),
  pairCan('pair.can', 'React Web can'),
  pairScopeRead('pair.scope.read', 'See performance, players and incidents'),
  pairScopeOperate('pair.scope.op_execute', 'Run actions and switch features'),
  pairScopeAdmin('pair.scope.admin', 'Change settings and run heavy actions'),
  pairScopeConsoleRead('pair.scope.console_read', 'Read the console'),
  pairScopeConsoleRun('pair.scope.console_execute', 'Run console commands'),
  pairBrowserName('pair.browser_name', 'Browser name'),
  pairBrowserNameTip(
    'pair.browser_name_tip',
    'How /react web tokens lists this browser.',
  ),
  pairRemember('pair.remember', 'Remember this browser'),
  pairRememberTip(
    'pair.remember_tip',
    'Off on a shared computer: the pairing then ends with this tab.',
  ),
  pairDetails('pair.details', 'Details'),
  pairFingerprint('pair.fingerprint', 'Fingerprint'),
  pairFingerprintTip(
    'pair.fingerprint_tip',
    'Must match what /react web status prints.',
  ),
  pairDirect('pair.direct', 'Direct'),
  pairRelay('pair.relay', 'Relay'),
  pairLinkName('pair.link_name', 'Link name'),
  pairPaired('pair.paired', 'Paired with {server}.', <String>{'server'}),
  pairProblemNotALink('pair.problem.not_a_link', 'This is not a pairing link.'),
  pairProblemUsed('pair.problem.used', 'This link was already used.'),
  pairProblemExpired('pair.problem.expired', 'This link expired.'),
  pairProblemRevoked('pair.problem.revoked', 'This link was revoked.'),
  pairProblemUnknown(
    'pair.problem.unknown',
    'The server does not know this link.',
  ),
  pairProblemOrigin(
    'pair.problem.origin',
    "The server does not accept this page's address.",
  ),
  pairProblemUnreachable(
    'pair.problem.unreachable',
    'The server did not answer.',
  ),
  pairProblemBlocked(
    'pair.problem.blocked',
    'The browser blocked the connection to your server.',
  ),
  pairProblemWrongServer(
    'pair.problem.wrong_server',
    'A different server answered.',
  ),
  pairProblemBusy('pair.problem.busy', 'The server is busy.'),
  pairProblemOther('pair.problem.other', 'The server refused the link.'),
  pairNextNewLink(
    'pair.next.new_link',
    'Run /react web pair again, then open or paste the new link.',
  ),
  pairNextNotALink(
    'pair.next.not_a_link',
    'Copy the whole link from the console or chat, or run /react web pair for a new one.',
  ),
  pairNextOrigin(
    'pair.next.origin',
    'Add {origin} to {key} in {file}, then press Pair again.',
    <String>{'origin', 'key', 'file'},
  ),
  pairNextUnreachable(
    'pair.next.unreachable',
    'Check that it is running, then press Pair again.',
  ),
  pairNextBlocked(
    'pair.next.blocked',
    "Allow local network access for this site in the browser's site settings, then press Pair again.",
  ),
  pairNextBusy('pair.next.busy', 'Wait a few seconds, then press Pair again.'),
  pairNextOther('pair.next.other', 'Press Pair again, or make a new link.'),
  pairEnded('pair.ended', 'Pairing ended.'),
  pairEndedState('pair.ended_state', 'Pairing ended'),
  pairNoDataSince('pair.no_data_since', 'No data since {time}.', <String>{
    'time',
  }),
  pairEndsInHours(
    'pair.ends_in_hours',
    'Pairing ends in {hours} h unless used.',
    <String>{'hours'},
  ),
  pairEndsTomorrow('pair.ends_tomorrow', 'Pairing ends in 1 day unless used.'),
  pairEndsIn(
    'pair.ends_in',
    'Pairing ends in {days} days unless used.',
    <String>{'days'},
  ),
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
  shellConnectionLost('shell.connection_lost', 'Connection lost'),
  shellConnectionDegraded('shell.connection_degraded', 'Connection degraded'),
  shellReconnect('shell.reconnect', 'Reconnect'),
  shellServerOffline('shell.server_offline', 'Server offline'),
  shellServerReconnected('shell.server_reconnected', 'Server reconnected'),
  languageSelect('language.select', 'Select language'),
  languageLoadFailed(
    'language.load_failed',
    'The selected language could not be loaded.',
  ),
  commonRetry('common.retry', 'Retry'),
  commonUnavailable('common.unavailable', 'Unavailable'),
  commonNotReported('common.not_reported', 'Not reported by this server'),
  commonActive('common.active', 'Active'),
  commonServer('common.server', 'Server'),
  commonState('common.state', 'State'),
  commonAlerts('common.alerts', 'Alerts'),
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
  shellOpenNavigation('shell.a11y.open_navigation', 'Open navigation'),
  shellCloseNavigation('shell.a11y.close_navigation', 'Close navigation'),
  shellAllServers('shell.all_servers', 'All servers'),
  shellSwitchServer('shell.switch_server', 'Switch server'),
  shellNavOperate('shell.nav.operate', 'Operate'),
  shellNavConfigure('shell.nav.configure', 'Configure'),
  shellSkipToContent('shell.skip_to_content', 'Skip to content'),
  shellMenu('shell.menu', 'Menu'),
  shellMoreOptions('shell.more_options', 'More options'),
  modeSimple('mode.simple', 'Simple'),
  modeAdvanced('mode.advanced', 'Advanced'),
  modeSwitchToAdvanced('mode.switch_to_advanced', 'Switch to Advanced'),
  modeSwitchToSimple('mode.switch_to_simple', 'Switch to Simple'),
  commonCancel('common.cancel', 'Cancel'),
  commonMoreActions('common.more_actions', 'More actions'),
  commonMoreInfo('common.more_info', 'More info'),
  commonLoading('common.loading', 'Loading'),
  pageActivityTitle('page.activity.title', 'Activity'),
  pageMapTitle('page.map.title', 'Map'),
  pageConsoleTitle('page.console.title', 'Console'),
  pageTuningTitle('page.tuning.title', 'Tuning'),
  pagePluginsTitle('page.plugins.title', 'Plugins'),
  pageActivityTip('page.activity.tip', 'Entities, chunks and world mechanics'),
  pageTuningTip(
    'page.tuning.tip',
    'Features, tweaks, world budgets and config',
  ),
  pagePluginsTip('page.plugins.tip', 'Plugin metrics and API packs'),
  pageSettingsTip(
    'page.settings.tip',
    'Look, saved servers, alerts and access',
  ),
  pageFleetTip('page.fleet.tip', 'Every paired server at a glance'),
  pageFleetServers('page.fleet.servers', 'Servers'),
  pageFleetCompare('page.fleet.compare', 'Compare'),
  pagePerformanceTicks('page.performance.ticks', 'Ticks'),
  pagePerformanceHost('page.performance.host', 'Host'),
  pageTuningFeatures('page.tuning.features', 'Features'),
  pageTuningConfig('page.tuning.config', 'Config'),
  pagePluginsApiPacks('page.plugins.api_packs', 'API packs'),
  chartTimeSeriesLabel(
    'chart.time_series_label',
    'Time series chart: {series}',
    <String>{'series'},
  ),
  chartSample('chart.sample', 'Sample {number}', <String>{'number'}),
  chartAwaitingSamples('chart.awaiting_samples', 'Awaiting samples'),
  chartKeysHint('chart.keys_hint', 'Arrow keys step through the samples'),
  chartSteady('chart.steady', 'Steady at {value}', <String>{'value'}),
  chartRange('chart.range', 'Time range'),
  chartRangeLive('chart.range_live', 'Live'),
  heatmapNoScoredChunks(
    'heatmap.no_scored_chunks',
    'No scored chunks were returned for this heatmap.',
  ),
  heatmapInteractionHint(
    'heatmap.interaction_hint',
    'Wheel to zoom · drag to pan',
  ),
  heatmapNoLoadedSamples(
    'heatmap.no_loaded_samples',
    'No currently loaded samples',
  ),
  heatmapChunkCoordinate('heatmap.chunk_coordinate', 'Chunk {x}, {z}', <String>{
    'x',
    'z',
  }),
  heatmapChunkRange(
    'heatmap.chunk_range',
    'Chunks X {minX}…{maxX} · Z {minZ}…{maxZ}',
    <String>{'minX', 'maxX', 'minZ', 'maxZ'},
  ),
  heatmapBlockRange(
    'heatmap.block_range',
    'Blocks X {minX}…{maxX} · Z {minZ}…{maxZ}',
    <String>{'minX', 'maxX', 'minZ', 'maxZ'},
  ),
  heatmapStatistics(
    'heatmap.statistics',
    'Peak {peak} · Average {average} · {samples} samples',
    <String>{'peak', 'average', 'samples'},
  ),
  heatmapNorthUpGrid(
    'heatmap.north_up_grid',
    '{label}, north-up coordinate grid',
    <String>{'label'},
  ),
  heatmapWorldBorderLabel(
    'heatmap.world_border_label',
    'World border centered at block {x}, {z}, size {size}',
    <String>{'x', 'z', 'size'},
  ),
  heatmapCopyPosition('heatmap.copy_position', 'Copy position'),
  heatmapPositionCopied('heatmap.position_copied', 'Position copied'),
  heatmapClipboardUnavailable(
    'heatmap.clipboard_unavailable',
    'Clipboard unavailable',
  ),
  heatmapPlayer('heatmap.player', 'Player'),
  heatmapSelectOnlinePlayer(
    'heatmap.select_online_player',
    'Select an online player',
  ),
  heatmapNoOnlinePlayers('heatmap.no_online_players', 'No online players'),
  heatmapPlayersLoading('heatmap.players_loading', 'Loading online players…'),
  heatmapTeleport('heatmap.teleport', 'Teleport'),
  heatmapTeleportConfirmTitle(
    'heatmap.teleport_confirm_title',
    'Teleport {player}?',
    <String>{'player'},
  ),
  heatmapTeleportConfirmMessage(
    'heatmap.teleport_confirm_message',
    '{world} · X {x}, Z {z}',
    <String>{'world', 'x', 'z'},
  ),
  heatmapTeleportQueued('heatmap.teleport_queued', 'Teleport queued'),
  heatmapTeleportFailed('heatmap.teleport_failed', 'Teleport failed'),
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
  actionsNoneAvailable('screen.actions.none_available', 'No actions available'),
  actionsUnavailable('screen.actions.unavailable', 'Actions unavailable'),
  alertsNoMatches('screen.alerts.no_matches', 'No alerts match these filters'),
  configNoSections(
    'screen.config_editor.no_sections',
    'No configuration sections',
  ),
  configUnavailable(
    'screen.config_editor.unavailable',
    'Configuration unavailable',
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
  fleetNoFilterMatches(
    'screen.fleet.no_filter_matches',
    'No servers match this filter',
  ),
  fleetAwaitingTelemetry(
    'screen.fleet.awaiting_telemetry',
    'Awaiting telemetry',
  ),
  heatmapsNoSpatialMetrics(
    'screen.heatmaps.no_spatial_metrics',
    'No spatial metrics',
  ),
  heatmapsRequestFailed(
    'screen.heatmaps.request_failed',
    'Heatmap request failed',
  ),
  heatmapsNoChunkHeatmaps(
    'screen.heatmaps.no_chunk_heatmaps',
    'No chunk heatmaps',
  ),
  heatmapsNoWorlds('screen.heatmaps.no_worlds', 'No worlds'),
  heatmapsNorth('screen.heatmaps.north', 'North'),
  heatmapsSouth('screen.heatmaps.south', 'South'),
  heatmapsEast('screen.heatmaps.east', 'East'),
  heatmapsWest('screen.heatmaps.west', 'West'),
  logsNoLines('screen.logs.no_lines', 'No log lines'),
  logsUnavailable('screen.logs.unavailable', 'Logs unavailable'),
  logsLoadingHistory(
    'screen.logs.loading_history',
    'Loading recent log lines…',
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
  overviewViewIncidents('screen.overview.view_incidents', 'View incidents'),
  overviewNoAlerts('screen.overview.no_alerts', 'No open alerts'),
  overviewQuickActions('screen.overview.quick_actions', 'Quick actions'),
  overviewAllActions('screen.overview.all_actions', 'All actions'),
  memoryJvmHeap('screen.memory.jvm_heap', 'JVM heap'),
  environmentOfTotal('screen.environment.of_total', 'of {total}', <String>{
    'total',
  }),
  environmentDisk('screen.environment.disk', 'Disk'),
  environmentSystemMounts('screen.environment.system_mounts', 'System mounts'),
  environmentRead('screen.environment.read', 'Read'),
  environmentWrite('screen.environment.write', 'Write'),
  environmentNetwork('screen.environment.network', 'Network'),
  environmentReceived('screen.environment.received', 'Received'),
  environmentSent('screen.environment.sent', 'Sent'),
  environmentField('screen.environment.field', 'Field'),
  environmentValue('screen.environment.value', 'Value'),
  metricsRangeAll('screen.metrics.range_all', 'All'),
  metricsNoSamples('screen.metrics.no_samples', 'No stored samples'),
  optimizationNoneAvailable(
    'screen.optimization.none_available',
    'No features',
  ),
  optimizationUnavailable(
    'screen.optimization.unavailable',
    'Features unavailable',
  ),
  settingsThresholdDescription(
    'screen.settings.threshold_description',
    'Warning and critical boundaries for the entire server group.',
  ),
  settingsTags('screen.settings.tags', 'Tags'),
  settingsNoTags('screen.settings.no_tags', 'No tags'),
  settingsRelayConnection(
    'screen.settings.relay_connection',
    'Relay connection',
  ),
  fleetRole('screen.fleet.role', 'Role'),
  fleetDirect('screen.fleet.direct', 'Direct'),
  fleetDirectAndRelay('screen.fleet.direct_and_relay', 'Direct and relay'),
  fleetPairAgain('screen.fleet.pair_again', 'Pair again'),
  fleetAllTags('screen.fleet.all_tags', 'All tags'),
  alertsFilters('screen.alerts.filters', 'Filters'),
  alertsAckAll('screen.alerts.ack_all', 'Acknowledge all'),
  alertsClearAll('screen.alerts.clear_all', 'Clear all'),
  alertsAlert('screen.alerts.alert', 'Alert'),
  alertsSeverity('screen.alerts.severity_label', 'Severity'),
  alertsAllSeverities('screen.alerts.all_severities', 'All severities'),
  alertsValue('screen.alerts.value', 'Value'),
  alertsSince('screen.alerts.since', 'Since'),
  alertsAckAllTitle(
    'screen.alerts.ack_all_title',
    'Acknowledge {count} alerts?',
    <String>{'count'},
  ),
  alertsClearAllTitle(
    'screen.alerts.clear_all_title',
    'Clear {count} alerts?',
    <String>{'count'},
  ),
  alertsClearAllMessage(
    'screen.alerts.clear_all_message',
    'Cleared alerts leave this list.',
  ),
  comparisonRank('screen.comparison.rank', 'Rank'),
  comparisonLowerBetter('screen.comparison.lower_better', 'Lower is better.'),
  comparisonHigherBetter(
    'screen.comparison.higher_better',
    'Higher is better.',
  ),
  fleetWrongServer('screen.fleet.wrong_server', 'Different server'),
  fleetTransport('screen.fleet.transport', 'Transport'),
  settingsTheme('screen.settings.theme', 'Theme'),
  settingsLanguage('screen.settings.language', 'Language'),
  settingsMode('screen.settings.mode', 'Mode'),
  settingsModeTip(
    'screen.settings.mode_tip',
    'Advanced adds Metrics, internals, events and raw configuration.',
  ),
  settingsAddress('screen.settings.address', 'Address'),
  settingsEdit('screen.settings.edit', 'Edit'),
  settingsEditServer('screen.settings.edit_server', 'Edit {server}', <String>{
    'server',
  }),
  settingsSave('screen.settings.save', 'Save'),
  settingsNameRequired('screen.settings.name_required', 'Enter a name.'),
  settingsTagsTip(
    'screen.settings.tags_tip',
    'Separate tags with commas. The server group can be filtered by tag.',
  ),
  settingsClearAllTitle(
    'screen.settings.clear_all_title',
    'Remove all servers?',
  ),
  settingsClearAllMessage(
    'screen.settings.clear_all_message',
    'Every saved server and its access token is removed from this browser.',
  ),
  settingsResetThresholdsTitle(
    'screen.settings.reset_thresholds_title',
    'Reset thresholds to defaults?',
  ),
  settingsThresholdInvalid(
    'screen.settings.threshold_invalid',
    'Enter a number of 0 or more.',
  ),
  settingsNoAccess('screen.settings.no_access', 'No access'),
  pageSettingsGeneral('page.settings.general', 'General'),
  tweaksNoneAvailable('screen.tweaks.none_available', 'No tweaks available'),
  tweaksUnavailable('screen.tweaks.unavailable', 'Tweaks unavailable'),
  worldOverridesUnavailable(
    'screen.world_overrides.unavailable',
    'World overrides unavailable',
  ),
  logsSearch('screen.logs.search', 'Search log'),
  logsJumpLatest('screen.logs.jump_latest', 'Jump to latest'),
  logsNoAccess('screen.logs.no_access', 'No access'),
  logsNoAccessDescription(
    'screen.logs.no_access_description',
    'Reading the console needs the console:read scope.',
  ),
  consoleCommand('screen.logs.console.command', 'Command'),
  actionsConfirmRun(
    'screen.actions.confirm_run',
    'Run {action} on {server}?',
    <String>{'action', 'server'},
  ),
  actionsRecentRuns('screen.actions.recent_runs', 'Recent runs'),
  actionsColumnAction('screen.actions.column_action', 'Action'),
  actionsColumnStarted('screen.actions.column_started', 'Started'),
  actionsStateQueued('screen.actions.state.queued', 'Queued'),
  actionsStateRunning('screen.actions.state.running', 'Running'),
  actionsStateDone('screen.actions.state.done', 'Done'),
  actionsStateFailed('screen.actions.state.failed', 'Failed'),
  optimizationSearch('screen.optimization.search', 'Search'),
  optimizationCategory('screen.optimization.category', 'Category'),
  optimizationAllCategories(
    'screen.optimization.all_categories',
    'All categories',
  ),
  optimizationColumnFeature('screen.optimization.column_feature', 'Feature'),
  optimizationNoMatch('screen.optimization.no_match', 'Nothing matches'),
  optimizationConfirmEnable(
    'screen.optimization.confirm_enable',
    'Enable {count} items on {server}?',
    <String>{'count', 'server'},
  ),
  optimizationConfirmDisable(
    'screen.optimization.confirm_disable',
    'Disable {count} items on {server}?',
    <String>{'count', 'server'},
  ),
  tweaksColumnTweak('screen.tweaks.column_tweak', 'Tweak'),
  worldOverridesColumnWorld('screen.world_overrides.column_world', 'World'),
  worldOverridesEdit('screen.world_overrides.edit', 'Edit'),
  configEditorPresetsTip(
    'screen.config_editor.presets_tip',
    'A preset sets many values at once.',
  ),
  configEditorSave('screen.config_editor.save', 'Save'),
  configEditorConfirmPreset(
    'screen.config_editor.confirm_preset',
    'Apply the {preset} preset?',
    <String>{'preset'},
  ),
  configEditorConfirmPresetMessage(
    'screen.config_editor.confirm_preset_message',
    'Your {count} unsaved changes are lost.',
    <String>{'count'},
  ),
  configEditorApplyPreset('screen.config_editor.apply_preset', 'Apply preset'),
  pluginApiColumnPack('screen.plugin_api.column_pack', 'Pack'),
  pluginApiColumnMetric('screen.plugin_api.column_metric', 'Metric'),
  pluginApiColumnSource('screen.plugin_api.column_source', 'Source'),
  pluginApiConfirmDeleteTitle(
    'screen.plugin_api.confirm_delete_title',
    'Delete {pack} from {server}?',
    <String>{'pack', 'server'},
  ),
  optimizationChangedOnServer(
    'screen.optimization.changed_on_server',
    'Changed on server',
  ),
  logsReconnecting('screen.logs.reconnecting', 'Reconnecting'),
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
