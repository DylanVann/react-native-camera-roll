#import "RCTImageLoader.h"
#import "PHCachingImageManagerInstance.h"

@interface RCTImageLoaderRNPhotosFramework : NSObject <RCTImageURLLoader>

- (float) loaderPriority;

@end
