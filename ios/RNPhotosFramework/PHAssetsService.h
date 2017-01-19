#import <Foundation/Foundation.h>
#import "PHAssetWithCollectionIndex.h"
@import Photos;
@interface PHAssetsService : NSObject

+(PHFetchResult<PHAsset *> *) getAssetsForParams:(NSDictionary *)params;
+(NSArray<NSDictionary *> *) assetsArrayToUriArray:(NSArray<id> *)assetsArray andincludeMetadata:(BOOL)includeMetadata andIncludeAssetResourcesMetadata:(BOOL)includeResourcesMetadata;
+(NSMutableArray<PHAssetWithCollectionIndex*> *) getAssetsForFetchResult:(PHFetchResult *)assetsFetchResult startIndex:(int)startIndex endIndex:(int)endIndex assetDisplayStartToEnd:(BOOL)assetDisplayStartToEnd andAssetDisplayBottomUp:(BOOL)assetDisplayBottomUp;
+(NSMutableArray<PHAssetWithCollectionIndex*> *) getAssetsForFetchResult:(PHFetchResult *)assetsFetchResult atIndecies:(NSArray<NSNumber *> *)indecies;
+(PHFetchResult<PHAsset *> *) getAssetsFromArrayOfLocalIdentifiers:(NSArray<NSString *> *)arrayWithLocalIdentifiers;
+(NSMutableDictionary *)extendAssetDictWithAssetMetadata:(NSMutableDictionary *)dictToExtend andPHAsset:(PHAsset *)asset;
+(NSMutableDictionary *)extendAssetDictWithAssetResourcesMetadata:(NSMutableDictionary *)dictToExtend andPHAsset:(PHAsset *)asset;
+(void)extendAssetDictWithPhotoAssetEditionMetadata:(NSMutableDictionary *)dictToExtend andPHAsset:(PHAsset *)asset andCompletionBlock:(void(^)(NSMutableDictionary * dict))completeBlock;
+(void)deleteAssets:(PHFetchResult<PHAsset *> *)assetsToDelete andCompleteBLock:(nullable void(^)(BOOL success, NSError *__nullable error, NSArray<NSString *> * localIdentifiers))completeBlock;
+(void)updateAssetWithParams:(NSDictionary *)params completionBlock:(void(^)(BOOL success, NSError * _Nullable error, NSString * _Nullable localIdentifier))completionBlock andAsset:(PHAsset *)asset;

@end
