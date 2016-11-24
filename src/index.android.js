import React from 'react-native'

const { CameraRoll } = React.NativeModules

// No default album needed for android.
const getDefaultAlbum = () => Promise.resolve({})

const getAlbums = () =>
  CameraRoll.getAlbums({})
  .then(res => res.albums)

const extractAsset = asset => ({
  id: asset.id,
  uri: asset.uri,
  source: asset.source,
  isVideo: asset.mediaType === 'video',
  timeStamp: asset.creationDate * 1000,
})

const getPhotos = (album, options) => {
  const allOptions = {
    albumId: album.id,
    ...options,
    after: `${options.after}`,
    first: options.first,
  }
  return CameraRoll.getPhotos(allOptions)
  .then(data => ({
    after: data.page_info.end_cursor,
    hasMore: data.page_info.has_next_page,
    assets: data.assets.map(extractAsset),
  }))
}

export default {
  getDefaultAlbum,
  getAlbums,
  getPhotos,
}
