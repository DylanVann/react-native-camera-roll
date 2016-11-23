import PhotosFramework from './ios'

export const getDefaultAlbum = () =>
  PhotosFramework.getAlbums([{
    type: 'smartAlbum',
    subType: 'smartAlbumUserLibrary',
    previewAssets: 1,
    prepareForSizeDisplay: this.getImageDimensions(),
    preCacheAssets: true,
    fetchOptions: {
      includeHiddenAssets: true,
      includeAllBurstAssets: false,
    },
  }])
  .then(res => this.album = res.albums[0])

export const getAlbums = options =>
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
  uri: asset.uri,
  isVideo: asset.mediaType === 'video',
  timeStamp: asset.creationDate * 1000,
  id: asset.localIdentifier,
})

export const getPhotos = (album, options) => {
  const allOptions = {
    startIndex: (options.after || 0),
    endIndex: (options.after || 0) + options.first,
    includeMetaData: true,
    includeHiddenAssets: true,
    fetchOptions: {
      includeHiddenAssets: true,
      includeAllBurstAssets: false,
    },
    ...options,
  }
  return album.getAssets(allOptions)
  .then(data => ({
    after: options.after + options.first,
    hasMore: data.assets.length === (options.endIndex - options.startIndex),
    assets: data.assets.map(extractAsset),
  }))
}
