import PhotosFramework from './ios'

const getDefaultAlbum = options =>
  PhotosFramework.getAlbums({
    type: 'smartAlbum',
    subType: 'smartAlbumUserLibrary',
    previewAssets: 1,
    preCacheAssets: true,
    fetchOptions: {
      includeHiddenAssets: true,
      includeAllBurstAssets: false,
    },
    ...options,
  })
  .then(res => res.albums[0])

const getAlbums = options =>
  PhotosFramework.getAlbumsMany([
    {
      type: 'smartAlbum',
      subType: 'any',
      assetCount: 'exact',
      previewAssets: 1,
      preCacheAssets: true,
      fetchOptions: {
        includeHiddenAssets: true,
        includeAllBurstAssets: false,
      },
      ...options,
    },
    {
      type: 'album',
      subType: 'any',
      assetCount: 'exact',
      previewAssets: 1,
      preCacheAssets: true,
      fetchOptions: {
        includeHiddenAssets: true,
        includeAllBurstAssets: false,
      },
      ...options,
    },
  ])
  .then((res) => {
    const joinedResults = res.reduce((prev, next) => prev.concat(next.albums), [])
    const albums = joinedResults.filter(album => album.previewAssets.length)
    return albums
  })

const extractAsset = asset => ({
  id: asset.localIdentifier,
  uri: asset.uri,
  source: asset.uri,
  isVideo: asset.mediaType === 'video',
  timeStamp: asset.creationDate * 1000,
})

const getPhotos = (album, options) => {
  const allOptions = {
    startIndex: (options.after || 0),
    endIndex: (options.after || 0) + options.first,
    includeMetadata: true,
    includeHiddenAssets: true,
    fetchOptions: {
      includeHiddenAssets: true,
      includeAllBurstAssets: false,
    },
    ...options,
  }
  return album.getAssets(allOptions)
  .then(data => ({
    after: allOptions.endIndex + 1,
    hasMore: data.assets.length === (options.first + 1),
    assets: data.assets.map(extractAsset),
  }))
}

export default {
  getDefaultAlbum,
  getAlbums,
  getPhotos,
}
