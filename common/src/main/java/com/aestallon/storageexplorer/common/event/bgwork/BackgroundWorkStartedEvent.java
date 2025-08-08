package com.aestallon.storageexplorer.common.event.bgwork;

import java.util.UUID;

public record BackgroundWorkStartedEvent(UUID uuid, String displayName) {}
