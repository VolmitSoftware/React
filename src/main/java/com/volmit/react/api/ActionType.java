package com.volmit.react.api;

import com.volmit.react.Info;

public enum ActionType {
    FIX_LIGHTING(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, Info.ACTION_FIX_LIGHTING_NAME, Info.ACTION_FIX_LIGHTING_DESCRIPTION),
    CPU_SCORE(ActionHandle.MUTEX, ActionTargetType.SYSTEM, "CPU Score", "Tempts the processor to turbo and catches the score."),
    THROTTLE_PHYSICS(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, "Throttle Physics", "Reduces physics ticking in the specified chunks."),
    DUMP(ActionHandle.MUTEX, ActionTargetType.SYSTEM, "Dump", "Dumps debugging and performance information into paste."),
    FILE_SIZE(ActionHandle.MUTEX, ActionTargetType.SYSTEM, "File Size", "Checks the server files for a size report."),
    TIMINGS(ActionHandle.MUTEX, ActionTargetType.SYSTEM, "Timings Report", "Pulls a timings report for some time."),
    CULL_ENTITIES(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, Info.ACTION_CULL_ENTITIES_NAME, Info.ACTION_CULL_ENTITIES_DESCRIPTION),
    COLLECT_GARBAGE(ActionHandle.MUTEX, ActionTargetType.SYSTEM, Info.ACTION_COLLECT_GARBAGE_NAME, Info.ACTION_COLLECT_GARBAGE_DESCRIPTION),
    PURGE_ENTITIES(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, Info.ACTION_PURGE_ENTITIES_NAME, Info.ACTION_PURGE_ENTITIES_DESCRIPTION),
    PURGE_CHUNKS(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, Info.ACTION_PURGE_CHUNKS_NAME, Info.ACTION_PURGE_CHUNKS_DESCRIPTION);

    private final String name;
    private final String description;
    private final ActionHandle handle;
    private final ActionTargetType target;

    ActionType(ActionHandle handle, ActionTargetType target, String name, String description) {
        this.name = name;
        this.description = description;
        this.handle = handle;
        this.target = target;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ActionHandle getHandle() {
        return handle;
    }

    public ActionTargetType getTarget() {
        return target;
    }
}
