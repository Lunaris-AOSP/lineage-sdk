/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.app;

import android.app.Notification;
import android.content.Context;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

import lineageos.os.Build;

import lineageos.os.Concierge;
import lineageos.os.Concierge.ParcelInfo;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.UUID;

/**
 * @hide
 * TODO: This isn't ready for public use
 */
public final class ProfileGroup implements Parcelable {
    private static final String TAG = "ProfileGroup";

    private String mName;
    private int mNameResId;

    private UUID mUuid;

    private Uri mSoundOverride = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    private Uri mRingerOverride = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

    private Mode mSoundMode = Mode.DEFAULT;
    private Mode mRingerMode = Mode.DEFAULT;
    private Mode mVibrateMode = Mode.DEFAULT;
    private Mode mLightsMode = Mode.DEFAULT;

    private boolean mDefaultGroup = false;
    private boolean mDirty;

    /** @hide */
    public static final Parcelable.Creator<ProfileGroup> CREATOR
            = new Parcelable.Creator<ProfileGroup>() {
        public ProfileGroup createFromParcel(Parcel in) {
            return new ProfileGroup(in);
        }

        @Override
        public ProfileGroup[] newArray(int size) {
            return new ProfileGroup[size];
        }
    };

    /** @hide */
    public ProfileGroup(UUID uuid, boolean defaultGroup) {
        this(null, uuid, defaultGroup);
    }

    private ProfileGroup(String name, UUID uuid, boolean defaultGroup) {
        mName = name;
        mUuid = (uuid != null) ? uuid : UUID.randomUUID();
        mDefaultGroup = defaultGroup;
        mDirty = uuid == null;
    }

    /** @hide */
    private ProfileGroup(Parcel in) {
        readFromParcel(in);
    }

    /** @hide */
    public boolean matches(NotificationGroup group, boolean defaultGroup) {
        if (mUuid.equals(group.getUuid())) {
            return true;
        }

        /* fallback matches for backwards compatibility */
        boolean matches = false;

        /* fallback attempt 1: match name */
        if (mName != null && mName.equals(group.getName())) {
            matches = true;
        /* fallback attempt 2: match for the 'defaultGroup' flag to match the wildcard group */
        } else if (mDefaultGroup && defaultGroup) {
            matches = true;
        }

        if (!matches) {
            return false;
        }

        mName = null;
        mUuid = group.getUuid();
        mDirty = true;

        return true;
    }

    public UUID getUuid() {
        return mUuid;
    }

    public boolean isDefaultGroup() {
        return mDefaultGroup;
    }

    /** @hide */
    public boolean isDirty() {
        return mDirty;
    }

    /** @hide */
    public void setSoundOverride(Uri sound) {
        mSoundOverride = sound;
        mDirty = true;
    }

    public Uri getSoundOverride() {
        return mSoundOverride;
    }

    /** @hide */
    public void setRingerOverride(Uri ringer) {
        mRingerOverride = ringer;
        mDirty = true;
    }

    public Uri getRingerOverride() {
        return mRingerOverride;
    }

    /** @hide */
    public void setSoundMode(Mode soundMode) {
        mSoundMode = soundMode;
        mDirty = true;
    }

    public Mode getSoundMode() {
        return mSoundMode;
    }

    /** @hide */
    public void setRingerMode(Mode ringerMode) {
        mRingerMode = ringerMode;
        mDirty = true;
    }

    public Mode getRingerMode() {
        return mRingerMode;
    }

    /** @hide */
    public void setVibrateMode(Mode vibrateMode) {
        mVibrateMode = vibrateMode;
        mDirty = true;
    }

    public Mode getVibrateMode() {
        return mVibrateMode;
    }

    /** @hide */
    public void setLightsMode(Mode lightsMode) {
        mLightsMode = lightsMode;
        mDirty = true;
    }

    public Mode getLightsMode() {
        return mLightsMode;
    }

    // TODO : add support for LEDs / screen etc.

    /** @hide */
    public void applyOverridesToNotification(Notification notification) {
        switch (mSoundMode) {
            case OVERRIDE:
                notification.sound = mSoundOverride;
                break;
            case SUPPRESS:
                notification.defaults &= ~Notification.DEFAULT_SOUND;
                notification.sound = null;
                break;
            case DEFAULT:
                break;
        }
        switch (mVibrateMode) {
            case OVERRIDE:
                notification.defaults |= Notification.DEFAULT_VIBRATE;
                break;
            case SUPPRESS:
                notification.defaults &= ~Notification.DEFAULT_VIBRATE;
                notification.vibrate = null;
                break;
            case DEFAULT:
                break;
        }
        switch (mLightsMode) {
            case OVERRIDE:
                notification.defaults |= Notification.DEFAULT_LIGHTS;
                break;
            case SUPPRESS:
                notification.defaults &= ~Notification.DEFAULT_LIGHTS;
                notification.flags &= ~Notification.FLAG_SHOW_LIGHTS;
                break;
            case DEFAULT:
                break;
        }
    }

    private boolean validateOverrideUri(Context context, Uri uri) {
        if (RingtoneManager.isDefault(uri)) {
            return true;
        }
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        boolean valid = false;

        if (cursor != null) {
            valid = cursor.moveToFirst();
            cursor.close();
        }
        return valid;
    }

    void validateOverrideUris(Context context) {
        if (!validateOverrideUri(context, mSoundOverride)) {
            mSoundOverride = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            mSoundMode = Mode.DEFAULT;
            mDirty = true;
        }
        if (!validateOverrideUri(context, mRingerOverride)) {
            mRingerOverride = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            mRingerMode = Mode.DEFAULT;
            mDirty = true;
        }
    }

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // Tell the concierge to prepare the parcel
        ParcelInfo parcelInfo = Concierge.prepareParcel(dest);

        // === BOYSENBERRY ===
        dest.writeString(mName);
        new ParcelUuid(mUuid).writeToParcel(dest, 0);
        dest.writeInt(mDefaultGroup ? 1 : 0);
        dest.writeInt(mDirty ? 1 : 0);
        dest.writeParcelable(mSoundOverride, flags);
        dest.writeParcelable(mRingerOverride, flags);
        dest.writeString(mSoundMode.name());
        dest.writeString(mRingerMode.name());
        dest.writeString(mVibrateMode.name());
        dest.writeString(mLightsMode.name());

        // Complete the parcel info for the concierge
        parcelInfo.complete();
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        // Read parcelable version via the Concierge
        ParcelInfo parcelInfo = Concierge.receiveParcel(in);
        int parcelableVersion = parcelInfo.getParcelVersion();

        // Pattern here is that all new members should be added to the end of
        // the writeToParcel method. Then we step through each version, until the latest
        // API release to help unravel this parcel
        if (parcelableVersion >= Build.LINEAGE_VERSION_CODES.BOYSENBERRY) {
            mName = in.readString();
            mUuid = ParcelUuid.CREATOR.createFromParcel(in).getUuid();
            mDefaultGroup = in.readInt() != 0;
            mDirty = in.readInt() != 0;
            mSoundOverride = in.readParcelable(null);
            mRingerOverride = in.readParcelable(null);

            mSoundMode = Mode.valueOf(Mode.class, in.readString());
            mRingerMode = Mode.valueOf(Mode.class, in.readString());
            mVibrateMode = Mode.valueOf(Mode.class, in.readString());
            mLightsMode = Mode.valueOf(Mode.class, in.readString());
        }

        // Complete parcel info for the concierge
        parcelInfo.complete();
    }

    public enum Mode {
        SUPPRESS, DEFAULT, OVERRIDE;
    }

    /** @hide */
    public void getXmlString(StringBuilder builder, Context context) {
        builder.append("<profileGroup uuid=\"");
        builder.append(TextUtils.htmlEncode(mUuid.toString()));
        if (mName != null) {
            builder.append("\" name=\"");
            builder.append(mName);
        }
        builder.append("\" default=\"");
        builder.append(isDefaultGroup());
        builder.append("\">\n<sound>");
        builder.append(TextUtils.htmlEncode(mSoundOverride.toString()));
        builder.append("</sound>\n<ringer>");
        builder.append(TextUtils.htmlEncode(mRingerOverride.toString()));
        builder.append("</ringer>\n<soundMode>");
        builder.append(mSoundMode);
        builder.append("</soundMode>\n<ringerMode>");
        builder.append(mRingerMode);
        builder.append("</ringerMode>\n<vibrateMode>");
        builder.append(mVibrateMode);
        builder.append("</vibrateMode>\n<lightsMode>");
        builder.append(mLightsMode);
        builder.append("</lightsMode>\n</profileGroup>\n");
        mDirty = false;
    }

    /** @hide */
    public static ProfileGroup fromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException, IOException {
        String name = xpp.getAttributeValue(null, "name");
        UUID uuid = null;
        String value = xpp.getAttributeValue(null, "uuid");

        if (value != null) {
            try {
                uuid = UUID.fromString(value);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "UUID not recognized for " + name + ", using new one.");
            }
        }

        value = xpp.getAttributeValue(null, "default");
        boolean defaultGroup = TextUtils.equals(value, "true");

        ProfileGroup profileGroup = new ProfileGroup(name, uuid, defaultGroup);
        int event = xpp.next();
        while (event != XmlPullParser.END_TAG || !xpp.getName().equals("profileGroup")) {
            if (event == XmlPullParser.START_TAG) {
                name = xpp.getName();
                if (name.equals("sound")) {
                    profileGroup.setSoundOverride(Uri.parse(xpp.nextText()));
                } else if (name.equals("ringer")) {
                    profileGroup.setRingerOverride(Uri.parse(xpp.nextText()));
                } else if (name.equals("soundMode")) {
                    profileGroup.setSoundMode(Mode.valueOf(xpp.nextText()));
                } else if (name.equals("ringerMode")) {
                    profileGroup.setRingerMode(Mode.valueOf(xpp.nextText()));
                } else if (name.equals("vibrateMode")) {
                    profileGroup.setVibrateMode(Mode.valueOf(xpp.nextText()));
                } else if (name.equals("lightsMode")) {
                    profileGroup.setLightsMode(Mode.valueOf(xpp.nextText()));
                }
            } else if (event == XmlPullParser.END_DOCUMENT) {
                throw new IOException("Premature end of file while parsing profleGroup:" + name);
            }
            event = xpp.next();
        }

        /* we just loaded from XML, no need to save */
        profileGroup.mDirty = false;

        return profileGroup;
    }
}
