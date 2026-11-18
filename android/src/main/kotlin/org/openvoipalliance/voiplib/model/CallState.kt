package org.openvoipalliance.voiplib.model

enum class CallState {
    Idle,
    IncomingReceived,
    PushIncomingReceived,
    OutgoingInit,
    OutgoingProgress,
    OutgoingRinging,
    OutgoingEarlyMedia,
    Connected,
    StreamsRunning,
    Pausing,
    Paused,
    Resuming,
    Referred,
    Error,
    End,
    PausedByRemote,
    UpdatedByRemote,
    IncomingEarlyMedia,
    Updating,
    Released,
    EarlyUpdatedByRemote,
    EarlyUpdating,
    Unknown
}