/**
 * Copyright (c) 2015-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.dylanvann.cameraroll;

import javax.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.text.TextUtils;

import com.facebook.common.logging.FLog;
import com.facebook.react.bridge.GuardedAsyncTask;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.JSApplicationIllegalArgumentException;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.common.ReactConstants;
import com.facebook.react.module.annotations.ReactModule;
import android.util.Log;

/**
 * {@link NativeModule} that allows JS to interact with the photos on the device (i.e.
 * {@link MediaStore.Images}).
 */
@ReactModule(name = "CameraRoll")
public class CameraRollManager extends ReactContextBaseJavaModule {

  private static final String ERROR_UNABLE_TO_LOAD = "E_UNABLE_TO_LOAD";
  private static final String ERROR_UNABLE_TO_LOAD_PERMISSION = "E_UNABLE_TO_LOAD_PERMISSION";
  private static final String ERROR_UNABLE_TO_SAVE = "E_UNABLE_TO_SAVE";

  @Override
  public String getName() {
    return "CameraRoll";
  }

  public static final boolean IS_JELLY_BEAN_OR_LATER =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;

  private static final String[] PROJECTION;
  static {
    if (IS_JELLY_BEAN_OR_LATER) {
      PROJECTION = new String[] {
          Files.FileColumns._ID,
          Files.FileColumns.DATE_ADDED,
          Files.FileColumns.MIME_TYPE,
          Files.FileColumns.MEDIA_TYPE,
          Files.FileColumns.WIDTH,
          Files.FileColumns.HEIGHT,
      };
    } else {
      PROJECTION = new String[] {
          Files.FileColumns._ID,
          Files.FileColumns.DATE_ADDED,
          Files.FileColumns.MIME_TYPE,
          Files.FileColumns.MEDIA_TYPE,
      };
    }
  }

  private static final String SELECTION_DATE_TAKEN = Files.FileColumns.DATE_ADDED + " < ?";

  public CameraRollManager(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  /**
   * Save an image to the gallery (i.e. {@link MediaStore.Images}). This copies the original file
   * from wherever it may be to the external storage pictures directory, so that it can be scanned
   * by the MediaScanner.
   *
   * @param uri the file:// URI of the image to save
   * @param promise to be resolved or rejected
   */
  @ReactMethod
  public void saveToCameraRoll(String uri, String type, Promise promise) {
    MediaType parsedType = type.equals("video") ? MediaType.VIDEO : MediaType.PHOTO;
    new SaveToCameraRoll(getReactApplicationContext(), Uri.parse(uri), parsedType, promise)
        .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private enum MediaType { PHOTO, VIDEO };
  private static class SaveToCameraRoll extends GuardedAsyncTask<Void, Void> {

    private final Context mContext;
    private final Uri mUri;
    private final Promise mPromise;
    private final MediaType mType;

    public SaveToCameraRoll(ReactContext context, Uri uri, MediaType type, Promise promise) {
      super(context);
      mContext = context;
      mUri = uri;
      mPromise = promise;
      mType = type;
    }

    @Override
    protected void doInBackgroundGuarded(Void... params) {
      File source = new File(mUri.getPath());
      FileChannel input = null, output = null;
      try {
        File exportDir = (mType == MediaType.PHOTO)
          ? Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
          : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        exportDir.mkdirs();
        if (!exportDir.isDirectory()) {
          mPromise.reject(ERROR_UNABLE_TO_LOAD, "External media storage directory not available");
          return;
        }
        File dest = new File(exportDir, source.getName());
        int n = 0;
        String fullSourceName = source.getName();
        String sourceName, sourceExt;
        if (fullSourceName.indexOf('.') >= 0) {
          sourceName = fullSourceName.substring(0, fullSourceName.lastIndexOf('.'));
          sourceExt = fullSourceName.substring(fullSourceName.lastIndexOf('.'));
        } else {
          sourceName = fullSourceName;
          sourceExt = "";
        }
        while (!dest.createNewFile()) {
          dest = new File(exportDir, sourceName + "_" + (n++) + sourceExt);
        }
        input = new FileInputStream(source).getChannel();
        output = new FileOutputStream(dest).getChannel();
        output.transferFrom(input, 0, input.size());
        input.close();
        output.close();

        MediaScannerConnection.scanFile(
            mContext,
            new String[]{dest.getAbsolutePath()},
            null,
            new MediaScannerConnection.OnScanCompletedListener() {
              @Override
              public void onScanCompleted(String path, Uri uri) {
                if (uri != null) {
                  mPromise.resolve(uri.toString());
                } else {
                  mPromise.reject(ERROR_UNABLE_TO_SAVE, "Could not add image to gallery");
                }
              }
            });
      } catch (IOException e) {
        mPromise.reject(e);
      } finally {
        if (input != null && input.isOpen()) {
          try {
            input.close();
          } catch (IOException e) {
            FLog.e(ReactConstants.TAG, "Could not close input channel", e);
          }
        }
        if (output != null && output.isOpen()) {
          try {
            output.close();
          } catch (IOException e) {
            FLog.e(ReactConstants.TAG, "Could not close output channel", e);
          }
        }
      }
    }
  }

  /**
   * Get photos from {@link MediaStore.Images}, most recent first.
   *
   * @param params a map containing the following keys:
   *        <ul>
   *          <li>first (mandatory): a number representing the number of photos to fetch</li>
   *          <li>
   *            after (optional): a cursor that matches page_info[end_cursor] returned by a
   *            previous call to {@link #getPhotos}
   *          </li>
   *          <li>groupName (optional): an album name</li>
   *          <li>
   *            mimeType (optional): restrict returned images to a specific mimetype (e.g.
   *            image/jpeg)
   *          </li>
   *        </ul>
   * @param promise the Promise to be resolved when the photos are loaded; for a format of the
   *        parameters passed to this callback, see {@code getPhotosReturnChecker} in CameraRoll.js
   */
  @ReactMethod
  public void getPhotos(final ReadableMap params, final Promise promise) {
    Log.i("GETPHOTOS", "GEETTTING THE PHOTOS");
    int first = params.getInt("first");
    String after = params.hasKey("after") ? params.getString("after") : null;
    String groupName = params.hasKey("groupName") ? params.getString("groupName") : null;
    ReadableArray mimeTypes = params.hasKey("mimeTypes")
        ? params.getArray("mimeTypes")
        : null;
    if (params.hasKey("groupTypes")) {
      throw new JSApplicationIllegalArgumentException("groupTypes is not supported on Android");
    }

    new GetPhotosTask(
          getReactApplicationContext(),
          first,
          after,
          groupName,
          mimeTypes,
          promise)
          .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private static class GetPhotosTask extends GuardedAsyncTask<Void, Void> {
    private final Context mContext;
    private final int mFirst;
    private final @Nullable String mAfter;
    private final @Nullable String mGroupName;
    private final @Nullable ReadableArray mMimeTypes;
    private final Promise mPromise;

    private GetPhotosTask(
        ReactContext context,
        int first,
        @Nullable String after,
        @Nullable String groupName,
        @Nullable ReadableArray mimeTypes,
        Promise promise) {
      super(context);
      mContext = context;
      mFirst = first;
      mAfter = after;
      mGroupName = groupName;
      mMimeTypes = mimeTypes;
      mPromise = promise;
    }

    @Override
    protected void doInBackgroundGuarded(Void... params) {
      StringBuilder selection = new StringBuilder("1");
      List<String> selectionArgs = new ArrayList<>();
      if (!TextUtils.isEmpty(mAfter)) {
        selection.append(" AND " + SELECTION_DATE_TAKEN);
        selectionArgs.add(mAfter);
      }
//      if (!TextUtils.isEmpty(mGroupName)) {
//        selection.append(" AND " + SELECTION_BUCKET);
//        selectionArgs.add(mGroupName);
//      }
      if (mMimeTypes != null && mMimeTypes.size() > 0) {
        selection.append(" AND " + Files.FileColumns.MIME_TYPE + " IN (");
        for (int i = 0; i < mMimeTypes.size(); i++) {
          selection.append("?,");
          selectionArgs.add(mMimeTypes.getString(i));
        }
        selection.replace(selection.length() - 1, selection.length(), ")");
      }
      WritableMap response = new WritableNativeMap();
      ContentResolver resolver = mContext.getContentResolver();
      // using LIMIT in the sortOrder is not explicitly supported by the SDK (which does not support
      // setting a limit at all), but it works because this specific ContentProvider is backed by
      // an SQLite DB and forwards parameters to it without doing any parsing / validation.
      try {
        Uri uri = Files.getContentUri("external");
        Cursor photos = resolver.query(
            uri,
            PROJECTION,
            selection.toString(),
            selectionArgs.toArray(new String[selectionArgs.size()]),
            Files.FileColumns.DATE_ADDED + " DESC, " + Files.FileColumns.DATE_MODIFIED + " DESC LIMIT " +
                (mFirst + 1)); // set LIMIT to first + 1 so that we know how to populate page_info
        if (photos == null) {
          mPromise.reject(ERROR_UNABLE_TO_LOAD, "Could not get photos");
        } else {
          try {
            putEdges(resolver, photos, response, mFirst);
            putPageInfo(photos, response, mFirst);
          } finally {
            photos.close();
            mPromise.resolve(response);
          }
        }
      } catch (SecurityException e) {
        mPromise.reject(
            ERROR_UNABLE_TO_LOAD_PERMISSION,
            "Could not get photos: need READ_EXTERNAL_STORAGE permission",
            e);
      }
    }
  }

  private static void putPageInfo(Cursor photos, WritableMap response, int limit) {
    WritableMap pageInfo = new WritableNativeMap();
    pageInfo.putBoolean("has_next_page", limit < photos.getCount());
    if (limit < photos.getCount()) {
      photos.moveToPosition(limit - 1);
      pageInfo.putString(
          "end_cursor",
          photos.getString(photos.getColumnIndex(Files.FileColumns.DATE_ADDED)));
    }
    response.putMap("page_info", pageInfo);
  }

  private static void putEdges(
      ContentResolver resolver,
      Cursor photos,
      WritableMap response,
      int limit) {
    WritableArray edges = new WritableNativeArray();
    photos.moveToFirst();
    int idIndex = photos.getColumnIndex(Files.FileColumns._ID);
    int mimeTypeIndex = photos.getColumnIndex(Files.FileColumns.MIME_TYPE);
    int mediaTypeIndex = photos.getColumnIndex(Files.FileColumns.MEDIA_TYPE);
    int dateAddedIndex = photos.getColumnIndex(Files.FileColumns.DATE_ADDED);
    int widthIndex = IS_JELLY_BEAN_OR_LATER ? photos.getColumnIndex(Files.FileColumns.WIDTH) : -1;
    int heightIndex = IS_JELLY_BEAN_OR_LATER ? photos.getColumnIndex(Files.FileColumns.HEIGHT) : -1;

    for (int i = 0; i < limit && !photos.isAfterLast(); i++) {
      WritableMap edge = new WritableNativeMap();
      WritableMap node = new WritableNativeMap();
      boolean imageInfoSuccess =
          putImageInfo(resolver, photos, node, mediaTypeIndex, idIndex, widthIndex, heightIndex);
      if (imageInfoSuccess) {
        putBasicNodeInfo(photos, node, mimeTypeIndex, 0, dateAddedIndex);
        edge.putMap("node", node);
        edges.pushMap(edge);
      } else {
        // we skipped an image because we couldn't get its details (e.g. width/height), so we
        // decrement i in order to correctly reach the limit, if the cursor has enough rows
        i--;
      }
      photos.moveToNext();
    }
    response.putArray("edges", edges);
  }

  private static void putBasicNodeInfo(
      Cursor photos,
      WritableMap node,
      int mimeTypeIndex,
      int groupNameIndex,
      int dateAddedIndex) {
    node.putString("type", photos.getString(mimeTypeIndex));
    node.putString("group_name", photos.getString(groupNameIndex));
    node.putDouble("timestamp", photos.getLong(dateAddedIndex) / 1000d);
  }

  private static boolean putImageInfo(
      ContentResolver resolver,
      Cursor photos,
      WritableMap node,
      int mediaTypeIndex,
      int idIndex,
      int widthIndex,
      int heightIndex) {
    WritableMap image = new WritableNativeMap();

    boolean isVideo = photos.getInt(mediaTypeIndex) == Files.FileColumns.MEDIA_TYPE_VIDEO;
    if (isVideo) {
      long videoId = photos.getLong(idIndex);
      MediaStore.Video.Thumbnails.getThumbnail(
              resolver,
              videoId,
              MediaStore.Video.Thumbnails.MINI_KIND,
              null);
      String[] projection = {
              MediaStore.Video.Thumbnails.DATA,
      };
      Cursor cursor = resolver.query(
              MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
              projection,
              MediaStore.Video.Thumbnails.VIDEO_ID + "=?",
              new String[] { String.valueOf(videoId) },
              null);
      cursor.moveToFirst();
      String uri = cursor.getString(0);
      image.putString("uri", "file://" + uri);
      Uri sourceUri = Uri.withAppendedPath(
              Video.Media.EXTERNAL_CONTENT_URI,
              photos.getString(idIndex));
      image.putString("source", sourceUri.toString());
    } else {
      Uri photoUri = Uri.withAppendedPath(
              Images.Media.EXTERNAL_CONTENT_URI,
              photos.getString(idIndex));
      image.putString("uri", photoUri.toString());
    }

    float width = -1;
    float height = -1;
    if (IS_JELLY_BEAN_OR_LATER) {
      width = photos.getInt(widthIndex);
      height = photos.getInt(heightIndex);
    }
    image.putDouble("width", width);
    image.putDouble("height", height);
    node.putMap("image", image);
    return true;
  }
}
