package com.ethanreilly.liftlog;

import android.service.notification.NotificationListenerService;

/**
 * Empty listener. Android only lets an app read other apps' media sessions
 * (what is playing, play/pause/skip) when the user has granted it
 * notification access, which requires this service to exist.
 */
public class MediaListener extends NotificationListenerService {}
