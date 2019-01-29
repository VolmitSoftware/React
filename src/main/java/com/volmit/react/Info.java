package com.volmit.react;

import org.bukkit.Color;
import org.bukkit.DyeColor;

public class Info
{
	public static String CORE_REACT_DOT = "react."; //$NON-NLS-1$
	public static String CORE_NAME = "React"; //$NON-NLS-1$
	public static String CORE_CACHE = "cache"; //$NON-NLS-1$
	public static String WORLD_CONFIGS = "worlds"; //$NON-NLS-1$
	public static String CORE_DOTYML = ".yml"; //$NON-NLS-1$

	public static String COMMAND_REACT = "react"; //$NON-NLS-1$
	public static String COMMAND_RAI = "rai"; //$NON-NLS-1$
	public static String COMMAND_ACT_DESCRIPTION = Lang.getString("command.act.description"); //$NON-NLS-1$
	public static String COMMAND_ACT = "action"; //$NON-NLS-1$
	public static String COMMAND_ACT_ALIAS_1 = "act"; //$NON-NLS-1$
	public static String COMMAND_ACT_ALIAS_2 = "a"; //$NON-NLS-1$
	public static String COMMAND_ACT_USAGE = "<action> [options]"; //$NON-NLS-1$

	public static String COMMAND_HELP_DESCRIPTION = Lang.getString("command.help.description"); //$NON-NLS-1$
	public static String COMMAND_HELP = "help"; //$NON-NLS-1$
	public static String COMMAND_HELP_ALIAS_1 = "h"; //$NON-NLS-1$
	public static String COMMAND_HELP_ALIAS_2 = "?"; //$NON-NLS-1$
	public static String COMMAND_HELP_USAGE = "[page]"; //$NON-NLS-1$

	public static String COMMAND_PING_DESCRIPTION = Lang.getString("command.ping.description"); //$NON-NLS-1$
	public static String COMMAND_PING = "ping"; //$NON-NLS-1$
	public static String COMMAND_PING_ALIAS_1 = "png"; //$NON-NLS-1$
	public static String COMMAND_PING_ALIAS_2 = "p"; //$NON-NLS-1$
	public static String COMMAND_PING_USAGE = "[player] [opts]"; //$NON-NLS-1$

	public static String COMMAND_STATUS_DESCRIPTION = Lang.getString("command.status.description"); //$NON-NLS-1$
	public static String COMMAND_STATUS = "status"; //$NON-NLS-1$
	public static String COMMAND_STATUS_ALIAS_1 = "stat"; //$NON-NLS-1$
	public static String COMMAND_STATUS_ALIAS_2 = "book"; //$NON-NLS-1$
	public static String COMMAND_STATUS_USAGE = ""; //$NON-NLS-1$

	public static String COMMAND_CPUSCORE_DESCRIPTION = Lang.getString("command.cpu-score.description"); //$NON-NLS-1$
	public static String COMMAND_CPUSCORE = "cpu-score"; //$NON-NLS-1$
	public static String COMMAND_CPUSCORE_ALIAS_1 = "cpus"; //$NON-NLS-1$
	public static String COMMAND_CPUSCORE_ALIAS_2 = "cs"; //$NON-NLS-1$
	public static String COMMAND_CPUSCORE_USAGE = ""; //$NON-NLS-1$

	public static String COMMAND_RELOAD_DESCRIPTION = Lang.getString("command.reload.description"); //$NON-NLS-1$
	public static String COMMAND_RELOAD = "reload"; //$NON-NLS-1$
	public static String COMMAND_RELOAD_ALIAS_1 = "rld"; //$NON-NLS-1$
	public static String COMMAND_RELOAD_ALIAS_2 = "rl"; //$NON-NLS-1$
	public static String COMMAND_RELOAD_USAGE = "[options]"; //$NON-NLS-1$

	public static String COMMAND_FEATURE_DESCRIPTION = Lang.getString("command.feature.description"); //$NON-NLS-1$
	public static String COMMAND_FEATURE = "feature"; //$NON-NLS-1$
	public static String COMMAND_FEATURE_ALIAS_1 = "f"; //$NON-NLS-1$
	public static String COMMAND_FEATURE_ALIAS_2 = "fe"; //$NON-NLS-1$
	public static String COMMAND_FEATURE_USAGE = ""; //$NON-NLS-1$

	public static String COMMAND_CTP_DESCRIPTION = Lang.getString("command.ctp.description"); //$NON-NLS-1$
	public static String COMMAND_CTP = "chunktp"; //$NON-NLS-1$
	public static String COMMAND_CTP_ALIAS_1 = "ctp"; //$NON-NLS-1$
	public static String COMMAND_CTP_ALIAS_2 = "chtp"; //$NON-NLS-1$
	public static String COMMAND_CTP_USAGE = "<world> <x> <z>"; //$NON-NLS-1$

	public static String COMMAND_CHUNK_DESCRIPTION = "Chunk stats"; //$NON-NLS-1$
	public static String COMMAND_CHUNK = "chunk"; //$NON-NLS-1$
	public static String COMMAND_CHUNK_ALIAS_1 = "c"; //$NON-NLS-1$
	public static String COMMAND_CHUNK_ALIAS_2 = "ch"; //$NON-NLS-1$
	public static String COMMAND_CHUNK_USAGE = ""; //$NON-NLS-1$

	public static String COMMAND_MONITOR_DESCRIPTION = Lang.getString("command.monitor.description"); //$NON-NLS-1$
	public static String COMMAND_MONITOR = "monitor"; //$NON-NLS-1$
	public static String COMMAND_MONITOR_ALIAS_1 = "mon"; //$NON-NLS-1$
	public static String COMMAND_MONITOR_ALIAS_2 = "m"; //$NON-NLS-1$
	public static String COMMAND_MONITOR_USAGE = "(toggle) [options]"; //$NON-NLS-1$

	public static String COMMAND_ENV_DESCRIPTION = Lang.getString("command.description.env"); //$NON-NLS-1$
	public static String COMMAND_ENV = "environment"; //$NON-NLS-1$
	public static String COMMAND_ENV_ALIAS_1 = "env"; //$NON-NLS-1$
	public static String COMMAND_ENV_ALIAS_2 = "ev"; //$NON-NLS-1$
	public static String COMMAND_ENV_USAGE = ""; //$NON-NLS-1$

	public static String COMMAND_FS_DESCRIPTION = "Get File Sizes"; //$NON-NLS-1$
	public static String COMMAND_FS = "filesize"; //$NON-NLS-1$
	public static String COMMAND_FS_ALIAS_1 = "files"; //$NON-NLS-1$
	public static String COMMAND_FS_ALIAS_2 = "fs"; //$NON-NLS-1$
	public static String COMMAND_FS_USAGE = ""; //$NON-NLS-1$

	public static String COMMAND_FIX_DESCRIPTION = Lang.getString("command.fix.description"); //$NON-NLS-1$
	public static String COMMAND_FIX = "fix"; //$NON-NLS-1$
	public static String COMMAND_FIX_ALIAS_1 = "fx"; //$NON-NLS-1$
	public static String COMMAND_FIX_ALIAS_2 = "auto"; //$NON-NLS-1$
	public static String COMMAND_FIX_USAGE = "[fix]"; //$NON-NLS-1$

	public static String COMMAND_VERSION_DESCRIPTION = Lang.getString("command.version.description"); //$NON-NLS-1$
	public static String COMMAND_VERSION = "version"; //$NON-NLS-1$
	public static String COMMAND_VERSION_ALIAS_1 = "ver"; //$NON-NLS-1$
	public static String COMMAND_VERSION_ALIAS_2 = "v"; //$NON-NLS-1$
	public static String COMMAND_VERSION_USAGE = ""; //$NON-NLS-1$

	public static String COMMAND_MAP_DESCRIPTION = Lang.getString("command.map.description"); //$NON-NLS-1$
	public static String COMMAND_MAP = "map"; //$NON-NLS-1$
	public static String COMMAND_MAP_ALIAS_1 = "mp"; //$NON-NLS-1$
	public static String COMMAND_MAP_ALIAS_2 = "pap"; //$NON-NLS-1$
	public static String COMMAND_MAP_USAGE = "(toggle) [options]"; //$NON-NLS-1$

	public static String COMMAND_TOPCHUNK_DESCRIPTION = Lang.getString("command.topchunk.description"); //$NON-NLS-1$
	public static String COMMAND_TOPCHUNK = "top"; //$NON-NLS-1$
	public static String COMMAND_TOPCHUNK_ALIAS_1 = "topchunk"; //$NON-NLS-1$
	public static String COMMAND_TOPCHUNK_ALIAS_2 = "lc"; //$NON-NLS-1$
	public static String COMMAND_TOPCHUNK_USAGE = ""; //$NON-NLS-1$

	public static String COMMAND_GLASS_DESCRIPTION = Lang.getString("command.glass.description"); //$NON-NLS-1$
	public static String COMMAND_GLASS = "glass"; //$NON-NLS-1$
	public static String COMMAND_GLASS_ALIAS_1 = "glasses"; //$NON-NLS-1$
	public static String COMMAND_GLASS_ALIAS_2 = "gg"; //$NON-NLS-1$
	public static String COMMAND_GLASS_USAGE = "(toggle)"; //$NON-NLS-1$

	public static String COMMAND_SUB_DESCRIPTION = Lang.getString("command.description.sub"); //$NON-NLS-1$
	public static String COMMAND_SUB = "subscribe"; //$NON-NLS-1$
	public static String COMMAND_SUB_ALIAS_1 = "sub"; //$NON-NLS-1$
	public static String COMMAND_SUB_ALIAS_2 = "su"; //$NON-NLS-1$
	public static String COMMAND_SUB_USAGE = "[channel]"; //$NON-NLS-1$

	public static String COMMAND_USUB_DESCRIPTION = Lang.getString("command.description.unsub"); //$NON-NLS-1$
	public static String COMMAND_USUB = "unsubscribe"; //$NON-NLS-1$
	public static String COMMAND_USUB_ALIAS_1 = "unsub"; //$NON-NLS-1$
	public static String COMMAND_USUB_ALIAS_2 = "usu"; //$NON-NLS-1$
	public static String COMMAND_USUB_USAGE = "[channel]"; //$NON-NLS-1$

	public static String COMMAND_CBLAME_DESCRIPTION = Lang.getString("command.cblame.description"); //$NON-NLS-1$
	public static String COMMAND_CBLAME = "blame"; //$NON-NLS-1$
	public static String COMMAND_CBLAME_ALIAS_1 = "cblame"; //$NON-NLS-1$
	public static String COMMAND_CBLAME_ALIAS_2 = "cb"; //$NON-NLS-1$
	public static String COMMAND_CBLAME_USAGE = ""; //$NON-NLS-1$

	public static String COMMAND_ACTIONLOG_DESCRIPTION = Lang.getString("command.action-log.description"); //$NON-NLS-1$
	public static String COMMAND_SCOREBOARDLOG = "scoreboard"; //$NON-NLS-1$
	public static String COMMAND_ACTIONLOG_ALIAS_1 = "score"; //$NON-NLS-1$
	public static String COMMAND_ACTIONLOG_ALIAS_2 = "sc"; //$NON-NLS-1$
	public static String COMMAND_ACTIONLOG_USAGE = "(toggle)"; //$NON-NLS-1$

	public static String COMMAND_TEMPACCESS_DESCRIPTION = Lang.getString("command.access.description"); //$NON-NLS-1$
	public static String COMMAND_TEMPACCESS = "access"; //$NON-NLS-1$
	public static String COMMAND_TEMPACCESS_ALIAS_1 = "rac"; //$NON-NLS-1$
	public static String COMMAND_TEMPACCESS_ALIAS_2 = "acc"; //$NON-NLS-1$
	public static String COMMAND_TEMPACCESS_USAGE = ""; //$NON-NLS-1$

	public static String COMMAND_ACCEPT_DESCRIPTION = Lang.getString("command.accept.description"); //$NON-NLS-1$
	public static String COMMAND_ACCEPT = "accept"; //$NON-NLS-1$
	public static String COMMAND_ACCEPT_ALIAS_1 = "tac"; //$NON-NLS-1$
	public static String COMMAND_ACCEPT_ALIAS_2 = "tacc"; //$NON-NLS-1$
	public static String COMMAND_ACCEPT_USAGE = "[player]"; //$NON-NLS-1$

	public static String COMMAND_REVOKE_DESCRIPTION = Lang.getString("command.revoke.description"); //$NON-NLS-1$
	public static String COMMAND_REVOKE = "revoke"; //$NON-NLS-1$
	public static String COMMAND_REVOKE_ALIAS_1 = "rv"; //$NON-NLS-1$
	public static String COMMAND_REVOKE_ALIAS_2 = "rev"; //$NON-NLS-1$
	public static String COMMAND_REVOKE_USAGE = "[player]"; //$NON-NLS-1$

	public static String COMMAND_REQUESTS_DESCRIPTION = Lang.getString("command.requests.description"); //$NON-NLS-1$
	public static String COMMAND_REQUESTS = "requests"; //$NON-NLS-1$
	public static String COMMAND_REQUESTS_ALIAS_1 = "req"; //$NON-NLS-1$
	public static String COMMAND_REQUESTS_ALIAS_2 = "rq"; //$NON-NLS-1$
	public static String COMMAND_REQUESTS_USAGE = ""; //$NON-NLS-1$

	public static String COMMAND_CAPABILITIES_DESCRIPTION = Lang.getString("command.description.cap"); //$NON-NLS-1$
	public static String COMMAND_CAPABILITIES = "capabilities"; //$NON-NLS-1$
	public static String COMMAND_CAPABILITIES_ALIAS_1 = "cap"; //$NON-NLS-1$
	public static String COMMAND_CAPABILITIES_ALIAS_2 = "ca"; //$NON-NLS-1$
	public static String COMMAND_CAPABILITIES_USAGE = ""; //$NON-NLS-1$

	public static String MSG_PERMISSION = Lang.getString("message.fail.no-permission"); //$NON-NLS-1$
	public static String MSG_MONITORING_STARTED = Lang.getString("message.status.monitoring.enabled"); //$NON-NLS-1$
	public static String MSG_MONITORING_STOPPED = Lang.getString("message.status.monitoring.disabled"); //$NON-NLS-1$
	public static String MSG_ACTIONLOGGING_STARTED = Lang.getString("message.status.logging.enabled"); //$NON-NLS-1$
	public static String MSG_ACTIONLOGGING_STOPPED = Lang.getString("message.status.logging.disabled"); //$NON-NLS-1$
	public static String MSG_GLASS_STARTED = Lang.getString("message.status.glass.enabled"); //$NON-NLS-1$
	public static String MSG_GLASS_STOPPED = Lang.getString("message.status.glass.disabled"); //$NON-NLS-1$

	public static Color COLOR_HOPPER = DyeColor.CYAN.getColor();
	public static Color COLOR_BLOCK_UPDATE = DyeColor.RED.getColor();
	public static Color COLOR_BLOCK_FROMTO = DyeColor.GREEN.getColor();
	public static Color COLOR_BLOCK_BURN = DyeColor.PURPLE.getColor();
	public static Color COLOR_BLOCK_DECAY = DyeColor.PURPLE.getColor();
	public static Color COLOR_BLOCK_FORM = DyeColor.PURPLE.getColor();

	public static String NAME_TICK = Lang.getString("monitor.tab.tick"); //$NON-NLS-1$
	public static String NAME_MEMORY = Lang.getString("monitor.tab.memory"); //$NON-NLS-1$
	public static String NAME_CHUNKS = Lang.getString("monitor.tab.chunks"); //$NON-NLS-1$
	public static String NAME_ENTITIES = Lang.getString("monitor.tab.entities"); //$NON-NLS-1$
	public static String NAME_ME = "WAILA";
	public static String NAME_BANDWIDTH = Lang.getString("command.bandwidth"); //$NON-NLS-1$

	public static String STATE_MONITORING_TAB = "monitor.tab"; //$NON-NLS-1$
	public static String STATE_MONITORING_ENABLED = "monitor.enabled"; //$NON-NLS-1$
	public static String STATE_MONITORING_HIGH = "monitor.high"; //$NON-NLS-1$
	public static String STATE_CHANNELS = "player.channels"; //$NON-NLS-1$
	public static String STATE_ACTIONLOGGING_ENABLED = "actionlogging.enabled"; //$NON-NLS-1$
	public static String STATE_MAPPING_ENABLED = "map.enabled"; //$NON-NLS-1$
	public static String STATE_MAPPING_SCROLLPOS = "map.scrollpos"; //$NON-NLS-1$
	public static String STATE_MAPPING_ARGS = "map.args"; //$NON-NLS-1$
	public static String STATE_SOUND_PLAYS = "player.sound-buffer"; //$NON-NLS-1$
	public static String STATE_MONITORING_POSTED = "monitor.posted"; //$NON-NLS-1$
	public static String STATE_MONITORING_LASTTAB = "monitor.last-tab"; //$NON-NLS-1$
	public static String STATE_MONITORING_SWT = "monitor.last-swt"; //$NON-NLS-1$
	public static String STATE_GLASSES_ENABLED = "glasses.enabled"; //$NON-NLS-1$
	public static String STATE_MONITORING_SWITCHNOTIFICATION = "monitor.switch-notification"; //$NON-NLS-1$
	public static String STATE_PLAYER_HOTBAR = "player.hotbar"; //$NON-NLS-1$
	public static String STATE_PLAYER_SHIFT = "player.shift"; //$NON-NLS-1$
	public static String STATE_PLAYER_SCROLL = "player.scroll"; //$NON-NLS-1$
	public static String STATE_PLAYER_HEIGHT_CURRENT = "player.height-current"; //$NON-NLS-1$
	public static String STATE_PLAYER_HEIGHT_CHANGING = "player.height-delta"; //$NON-NLS-1$

	public static String PERM_SYSTEMINFO = "systeminfo"; //$NON-NLS-1$
	public static String PERM_ACCESS = "access"; //$NON-NLS-1$
	public static String PERM_TPS = "tps"; //$NON-NLS-1$
	public static String PERM_TELEPORT = "teleport"; //$NON-NLS-1$
	public static String PERM_PING = "ping"; //$NON-NLS-1$
	public static String PERM_PING_OTHERS = "ping.others"; //$NON-NLS-1$
	public static String PERM_MONITOR = "monitor"; //$NON-NLS-1$
	public static String PERM_MONITOR_TITLE = PERM_MONITOR + ".title"; //$NON-NLS-1$
	public static String PERM_MONITOR_ACTIONLOG = PERM_MONITOR + ".actionlog"; //$NON-NLS-1$
	public static String PERM_MONITOR_MAP = PERM_MONITOR + ".map"; //$NON-NLS-1$
	public static String PERM_MONITOR_ENVIRONMENT = PERM_MONITOR + ".environment"; //$NON-NLS-1$
	public static String PERM_MONITOR_GLASSES = PERM_MONITOR + ".glasses"; //$NON-NLS-1$
	public static String PERM_MONITOR_CHUNK_BLAME = PERM_MONITOR + ".chunkblame"; //$NON-NLS-1$
	public static String PERM_ACT = "act"; //$NON-NLS-1$
	public static String PERM_RAI = "rai"; //$NON-NLS-1$
	public static String PERM_RAI_CONTROL = PERM_RAI + ".control"; //$NON-NLS-1$
	public static String PERM_RAI_ACCESS = PERM_RAI + ".access"; //$NON-NLS-1$
	public static String PERM_RAI_MONITOR = PERM_RAI + ".monitor"; //$NON-NLS-1$
	public static String PERM_RELOAD = "reload"; //$NON-NLS-1$

	public static String SAMPLER_REDSTONE_TICK_USAGE = Lang.getString("sampler.name.redstone-utilization"); //$NON-NLS-1$
	public static String SAMPLER_REDSTONE_TICK = Lang.getString("sampler.name.redstone-tick"); //$NON-NLS-1$
	public static String SAMPLER_ENTITY_TIME = Lang.getString("sampler.name.entity-time"); //$NON-NLS-1$
	public static String SAMPLER_REACT_TIME = "React Time"; //$NON-NLS-1$
	public static String SAMPLER_REACT_TASK_TIME = "React Task Time"; //$NON-NLS-1$
	public static String SAMPLER_ENTITY_TIME_LOCK = Lang.getString("sampler.name.entity-throttle"); //$NON-NLS-1$
	public static String SAMPLER_TILE_TIME_LOCK = Lang.getString("sampler.name.tile-throttle"); //$NON-NLS-1$
	public static String SAMPLER_TILE_TIME = Lang.getString("sampler.name.tile-time"); //$NON-NLS-1$
	public static String SAMPLER_TILE_DROPTICK = Lang.getString("sampler.name.tile-drop-tick"); //$NON-NLS-1$
	public static String SAMPLER_ENTITY_DROPTICK = Lang.getString("sampler.name.entity-drop-tick"); //$NON-NLS-1$
	public static String SAMPLER_REDSTONE_SECOND = Lang.getString("sampler.name.redstone.second"); //$NON-NLS-1$
	public static String SAMPLER_REDSTONE_TIME = Lang.getString("sampler.name.redstone-time"); //$NON-NLS-1$
	public static String SAMPLER_PHYSICS_TIME = Lang.getString("sampler.name.physics-time"); //$NON-NLS-1$
	public static String SAMPLER_HOPPER_TICK_USAGE = Lang.getString("sampler.name.hopper-utilization"); //$NON-NLS-1$
	public static String SAMPLER_HOPPER_TICK = Lang.getString("sampler.name.hopper-tick"); //$NON-NLS-1$
	public static String SAMPLER_HOPPER_SECOND = Lang.getString("sampler.name.hopper-second"); //$NON-NLS-1$
	public static String SAMPLER_HOPPER_TIME = Lang.getString("sampler.name.hopper-time"); //$NON-NLS-1$
	public static String SAMPLER_FLUID_TICK_USAGE = Lang.getString("sampler.name.fluid-utilization"); //$NON-NLS-1$
	public static String SAMPLER_FLUID_TICK = Lang.getString("sampler.name.fluid-tick"); //$NON-NLS-1$
	public static String SAMPLER_FLUID_SECOND = Lang.getString("sampler.name.fluid-second"); //$NON-NLS-1$
	public static String SAMPLER_FLUID_TIME = Lang.getString("sampler.name.fluid-time"); //$NON-NLS-1$
	public static String SAMPLER_TPS = Lang.getString("sampler.name.ticks-per-second"); //$NON-NLS-1$
	public static String SAMPLER_TICK = Lang.getString("sampler.name.server-tick-time"); //$NON-NLS-1$
	public static String SAMPLER_CPU = Lang.getString("command.cpu"); //$NON-NLS-1$
	public static String SAMPLER_PLAYERCOUNT = "playercount"; //$NON-NLS-1$
	public static String SAMPLER_PPS = Lang.getString("command.pps"); //$NON-NLS-1$
	public static String SAMPLER_BANDWIDTH = Lang.getString("command.bandwidth"); //$NON-NLS-1$
	public static String SAMPLER_BANDWIDTH_UP = Lang.getString("command.bandwidth-up"); //$NON-NLS-1$
	public static String SAMPLER_BANDWIDTH_DOWN = Lang.getString("command.bandwidth-down"); //$NON-NLS-1$
	public static String SAMPLER_TIU = Lang.getString("sampler.name.server-tick-utilization"); //$NON-NLS-1$
	public static String SAMPLER_MEM = Lang.getString("sampler.name.used-memory"); //$NON-NLS-1$
	public static String SAMPLER_FREEMEM = Lang.getString("sampler.name.free-memory"); //$NON-NLS-1$
	public static String SAMPLER_MEMTOTALS = "memtotals"; //$NON-NLS-1$
	public static String SAMPLER_MAXMEM = Lang.getString("sampler.name.max-memory"); //$NON-NLS-1$
	public static String SAMPLER_ALLOCMEM = Lang.getString("sampler.name.allocated-memory"); //$NON-NLS-1$
	public static String SAMPLER_MAHS = Lang.getString("sampler.name.memory-allocated-per-second"); //$NON-NLS-1$
	public static String SAMPLER_CHK = Lang.getString("sampler.name.loaded-chunks"); //$NON-NLS-1$
	public static String SAMPLER_CHK_TIME = Lang.getString("sampler.name.chunk-time"); //$NON-NLS-1$
	public static String SAMPLER_EXPLOSION_TIME = Lang.getString("command.explosion-ms"); //$NON-NLS-1$
	public static String SAMPLER_GROWTH_TIME = Lang.getString("command.growth-ms"); //$NON-NLS-1$
	public static String SAMPLER_CHKS = Lang.getString("sampler.name.chunks-second"); //$NON-NLS-1$
	public static String SAMPLER_ENT = Lang.getString("sampler.name.entities-count"); //$NON-NLS-1$
	public static String SAMPLER_ENTLIV = Lang.getString("sampler.name.entities-living-count"); //$NON-NLS-1$
	public static String SAMPLER_ENTDROP = Lang.getString("sampler.name.entities-drops-count"); //$NON-NLS-1$
	public static String SAMPLER_ENTTILE = Lang.getString("sampler.name.entities-tiles-count"); //$NON-NLS-1$

	public static String[] ACTION_CULL_ENTITIES_TAGS = new String[] {"cull-entities", "ce"}; //$NON-NLS-1$ //$NON-NLS-2$
	public static String ACTION_CULL_ENTITIES_STATUS = Lang.getString("action.cull-entities.status"); //$NON-NLS-1$
	public static String ACTION_CULL_ENTITIES_NAME = Lang.getString("action.cull-entities.name"); //$NON-NLS-1$
	public static String ACTION_CULL_ENTITIES_DESCRIPTION = Lang.getString("action.cull-entities.description"); //$NON-NLS-1$

	public static String[] ACTION_UPDATE_FLUID_TAGS = new String[] {"update-fluid", "upf"}; //$NON-NLS-1$ //$NON-NLS-2$
	public static String ACTION_UPDATE_FLUID_STATUS = "Updating Fluid $c / $t ($p)"; //$NON-NLS-1$
	public static String ACTION_UPDATE_FLUID_NAME = "Update Fluid"; //$NON-NLS-1$
	public static String ACTION_UPDATE_FLUID_DESCRIPTION = "Updates (restarts) ALL fluids in various chunks."; //$NON-NLS-1$

	public static String[] ACTION_LOCK_FLUID_TAGS = new String[] {"lock-fluid", "lf"}; //$NON-NLS-1$ //$NON-NLS-2$
	public static String ACTION_LOCK_FLUID_STATUS = Lang.getString("action.lock-fluid.status"); //$NON-NLS-1$
	public static String ACTION_LOCK_FLUID_NAME = Lang.getString("action.lock-fluid.name"); //$NON-NLS-1$
	public static String ACTION_LOCK_FLUID_DESCRIPTION = Lang.getString("action.lock-fluid.description"); //$NON-NLS-1$

	public static String[] ACTION_UNLOCK_FLUID_TAGS = new String[] {"unlock-fluid", "ulf"}; //$NON-NLS-1$ //$NON-NLS-2$
	public static String ACTION_UNLOCK_FLUID_STATUS = Lang.getString("action.unlock-fluid.status"); //$NON-NLS-1$
	public static String ACTION_UNLOCK_FLUID_NAME = Lang.getString("action.unlock-fluid.name"); //$NON-NLS-1$
	public static String ACTION_UNLOCK_FLUID_DESCRIPTION = Lang.getString("action.unlock-fluid.description"); //$NON-NLS-1$

	public static String[] ACTION_LOCK_HOPPER_TAGS = new String[] {"lock-hopper", "lh"}; //$NON-NLS-1$ //$NON-NLS-2$
	public static String ACTION_LOCK_HOPPER_STATUS = Lang.getString("action.lock-hopper.status"); //$NON-NLS-1$
	public static String ACTION_LOCK_HOPPER_NAME = Lang.getString("action.lock-hopper.name"); //$NON-NLS-1$
	public static String ACTION_LOCK_HOPPER_DESCRIPTION = Lang.getString("action.lock-hopper.description"); //$NON-NLS-1$

	public static String[] ACTION_UNLOCK_HOPPER_TAGS = new String[] {"unlock-hopper", "ulh"}; //$NON-NLS-1$ //$NON-NLS-2$
	public static String ACTION_UNLOCK_HOPPER_STATUS = Lang.getString("action.unlock-hopper.status"); //$NON-NLS-1$
	public static String ACTION_UNLOCK_HOPPER_NAME = Lang.getString("action.unlock-hopper.name"); //$NON-NLS-1$
	public static String ACTION_UNLOCK_HOPPER_DESCRIPTION = Lang.getString("action.unlock-hopper.description"); //$NON-NLS-1$

	public static String[] ACTION_LOCK_REDSTONE_TAGS = new String[] {"lock-redstone", "lr"}; //$NON-NLS-1$ //$NON-NLS-2$
	public static String ACTION_LOCK_REDSTONE_STATUS = Lang.getString("action.lock-redstone.status"); //$NON-NLS-1$
	public static String ACTION_LOCK_REDSTONE_NAME = Lang.getString("action.lock-redstone.name"); //$NON-NLS-1$
	public static String ACTION_LOCK_REDSTONE_DESCRIPTION = Lang.getString("action.lock-redstone.description"); //$NON-NLS-1$

	public static String[] ACTION_UNLOCK_REDSTONE_TAGS = new String[] {"unlock-redstone", "ulr"}; //$NON-NLS-1$ //$NON-NLS-2$
	public static String ACTION_UNLOCK_REDSTONE_STATUS = Lang.getString("action.unlock-redstone.status"); //$NON-NLS-1$
	public static String ACTION_UNLOCK_REDSTONE_NAME = Lang.getString("action.unlock-redstone.name"); //$NON-NLS-1$
	public static String ACTION_UNLOCK_REDSTONE_DESCRIPTION = Lang.getString("action.unlock-redstone.description"); //$NON-NLS-1$

	public static String[] ACTION_PURGE_ENTITIES_TAGS = new String[] {"purge-entities", "pe"}; //$NON-NLS-1$ //$NON-NLS-2$
	public static String ACTION_PURGE_ENTITIES_STATUS = Lang.getString("action.purge-entities.status"); //$NON-NLS-1$
	public static String ACTION_PURGE_ENTITIES_NAME = Lang.getString("action.purge-entities.name"); //$NON-NLS-1$
	public static String ACTION_PURGE_ENTITIES_DESCRIPTION = Lang.getString("action.purge-entities.description"); //$NON-NLS-1$

	public static String[] ACTION_PURGE_CHUNKS_TAGS = new String[] {"purge-chunks", "pc"}; //$NON-NLS-1$ //$NON-NLS-2$
	public static String ACTION_PURGE_CHUNKS_STATUS = Lang.getString("action.purge-chunks.status"); //$NON-NLS-1$
	public static String ACTION_PURGE_CHUNKS_NAME = Lang.getString("action.purge-chunks.name"); //$NON-NLS-1$
	public static String ACTION_PURGE_CHUNKS_DESCRIPTION = Lang.getString("action.purge-chunks.description"); //$NON-NLS-1$

	public static String[] ACTION_FIX_LIGHTING_TAGS = new String[] {"fix-lighting", "fl"}; //$NON-NLS-1$ //$NON-NLS-2$
	public static String ACTION_FIX_LIGHTING_STATUS = Lang.getString("action.fix-lighting.status"); //$NON-NLS-1$
	public static String ACTION_FIX_LIGHTING_NAME = Lang.getString("action.fix-lighting.name"); //$NON-NLS-1$
	public static String ACTION_FIX_LIGHTING_DESCRIPTION = Lang.getString("action.fix-lighting.description"); //$NON-NLS-1$

	public static String[] ACTION_CHUNK_TEST_TAGS = new String[] {"chunk-test", "ctest", "ct"}; //$NON-NLS-1$ //$NON-NLS-2$
	public static String ACTION_CHUNK_TEST_STATUS = "Testing..."; //$NON-NLS-1$
	public static String ACTION_CHUNK_TEST_NAME = "Chunk Test"; //$NON-NLS-1$
	public static String ACTION_CHUNK_TEST_DESCRIPTION = "Test chunks in an area around you and record all events."; //$NON-NLS-1$

	public static String[] ACTION_TILL_LAND_TAGS = new String[] {"till-land", "tl", "till", "tland"}; //$NON-NLS-1$ //$NON-NLS-2$
	public static String ACTION_TILL_LAND_STATUS = "Tilling $c / $t ($p)"; //$NON-NLS-1$
	public static String ACTION_TILL_LAND_NAME = "Till Land"; //$NON-NLS-1$
	public static String ACTION_TILL_LAND_DESCRIPTION = "Tills land"; //$NON-NLS-1$

	public static String[] ACTION_COLLECT_GARBAGE_TAGS = new String[] {"collect-garbage", "gc"}; //$NON-NLS-1$ //$NON-NLS-2$
	public static String ACTION_COLLECT_GARBAGE = "Collecting Garbage"; //$NON-NLS-1$
	public static String ACTION_COLLECT_GARBAGE_NAME = "Collect Garbage"; //$NON-NLS-1$
	public static String ACTION_COLLECT_GARBAGE_DESCRIPTION = "Runs GC on the server"; //$NON-NLS-1$
}
