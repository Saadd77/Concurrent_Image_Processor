package processor;

import config.ProcessingConfig;
import model.FilterType;
import model.ProcessingStats;
import task.TileProcessingTask;
import util.ImageUtils;
import util.VectorUtils;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fixed image processing class with controlled thread pool size and proper resource management
 */
public class ImageProcessor {

	// FIXED: Limited thread pool size to prevent memory exhaustion
	private static final int MAX_THREAD_POOL_SIZE = 8;
	private static final int TILE_SIZE = 256;

	// Thread-local storage for Vector API operations
	private static final ThreadLocal<int[]> THREAD_LOCAL_SRC_BUFFER = ThreadLocal.withInitial(() -> new int[1024 * 1024]);
	private static final ThreadLocal<int[]> THREAD_LOCAL_DST_BUFFER = ThreadLocal.withInitial(() -> new int[1024 * 1024]);

	// Synchronization for Vector API operations (if needed)
	private static final ReentrantLock VECTOR_LOCK = new ReentrantLock();

	/**
	 * Sequential image processing baseline for comparison
	 */
	public static ProcessingStats processImagesSequential(List<Path> imagePaths,
	                                                      Path outputDir,
	                                                      ProcessingConfig config) {
		System.out.println("=== SEQUENTIAL PROCESSING ===");
		ProcessingStats stats = new ProcessingStats();
		long startTime = System.currentTimeMillis();

		for (Path imagePath : imagePaths) {
			try {
				System.out.printf("Processing: %s%n", imagePath.getFileName());

				// Load image
				BufferedImage image = ImageUtils.loadImage(imagePath);
				if (image == null) {
					System.err.println("Failed to load: " + imagePath);
					stats.failedImages++;
					continue;
				}

				// Apply filters sequentially
				BufferedImage processed = applyFiltersSequential(image, config.getFilters());

				// Resize
				if (config.getTargetWidth() > 0 && config.getTargetHeight() > 0) {
					processed = ImageUtils.resizeImage(processed, config.getTargetWidth(), config.getTargetHeight());
				}

				// Save result
				Path outputPath = outputDir.resolve("seq_" + imagePath.getFileName());
				ImageUtils.saveImage(processed, outputPath, config.getCompressionQuality());

				stats.imagesProcessed++;

			} catch (Exception e) {
				System.err.println("Error processing " + imagePath + ": " + e.getMessage());
				stats.failedImages++;
			}
		}

		stats.totalTime = System.currentTimeMillis() - startTime;
		System.out.println("Sequential: " + stats);
		return stats;
	}

	/**
	 * FIXED: Parallel image processing with limited thread pool and proper task batching
	 */
	public static ProcessingStats processImagesParallel(List<Path> imagePaths,
	                                                    Path outputDir,
	                                                    ProcessingConfig config) {
		System.out.println("=== PARALLEL PROCESSING (Fixed Thread Pool) ===");
		System.out.printf("Using fixed thread pool size: %d threads%n", MAX_THREAD_POOL_SIZE);

		ProcessingStats stats = new ProcessingStats();
		long startTime = System.currentTimeMillis();

		// FIXED: Use fixed thread pool instead of one thread per image
		ExecutorService executor = Executors.newFixedThreadPool(MAX_THREAD_POOL_SIZE);

		// Process images in batches to control memory usage
		int batchSize = Math.max(1, MAX_THREAD_POOL_SIZE * 2); // Process in small batches
		List<List<Path>> batches = createBatches(imagePaths, batchSize);

		System.out.printf("Processing %d images in %d batches (batch size: %d)%n",
				imagePaths.size(), batches.size(), batchSize);

		for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
			List<Path> batch = batches.get(batchIndex);
			System.out.printf("Processing batch %d/%d (%d images)%n",
					batchIndex + 1, batches.size(), batch.size());

			List<Future<Boolean>> futures = new ArrayList<>();

			// Submit batch tasks
			for (Path imagePath : batch) {
				Future<Boolean> future = executor.submit(() -> {
					try {
						System.out.printf("Processing: %s [Thread: %s]%n",
								imagePath.getFileName(), Thread.currentThread().getName());

						BufferedImage image = ImageUtils.loadImage(imagePath);
						if (image == null) {
							System.err.println("Failed to load: " + imagePath);
							return false;
						}

						BufferedImage processed;
						if (config.isUseTileParallelism()) {
							processed = applyFiltersParallelTiles(image, config.getFilters());
						} else {
							processed = applyFiltersSequential(image, config.getFilters());
						}

						if (config.getTargetWidth() > 0 && config.getTargetHeight() > 0) {
							processed = ImageUtils.resizeImage(processed, config.getTargetWidth(), config.getTargetHeight());
						}

						Path outputPath = outputDir.resolve("parallel_" + imagePath.getFileName());
						ImageUtils.saveImage(processed, outputPath, config.getCompressionQuality());

						return true;
					} catch (OutOfMemoryError e) {
						System.err.println("Out of memory processing " + imagePath + ": " + e.getMessage());
						// Force garbage collection
						System.gc();
						return false;
					} catch (Exception e) {
						System.err.println("Error processing " + imagePath + ": " + e.getMessage());
						return false;
					}
				});
				futures.add(future);
			}

			// Wait for batch completion with timeout
			for (Future<Boolean> future : futures) {
				try {
					Boolean result = future.get(60, TimeUnit.SECONDS);
					if (result) {
						stats.imagesProcessed++;
					} else {
						stats.failedImages++;
					}
				} catch (TimeoutException e) {
					System.err.println("Task timed out - cancelling");
					future.cancel(true);
					stats.failedImages++;
				} catch (Exception e) {
					System.err.println("Task failed: " + e.getMessage());
					stats.failedImages++;
				}
			}

			// Clear futures list to help GC
			futures.clear();

			// Force garbage collection between batches to free memory
			if (batchIndex < batches.size() - 1) {
				System.gc();
				Thread.yield(); // Give GC time to work
			}
		}

		// Proper shutdown
		executor.shutdown();
		try {
			if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
				System.err.println("Executor did not terminate gracefully, forcing shutdown");
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}

		stats.totalTime = System.currentTimeMillis() - startTime;
		System.out.println("Parallel (Fixed Pool): " + stats);
		return stats;
	}

	/**
	 * Vector API image processing for SIMD acceleration
	 */
	public static ProcessingStats processImagesVector(List<Path> imagePaths,
	                                                  Path outputDir,
	                                                  ProcessingConfig config) {
		System.out.println("=== VECTOR API PROCESSING (SIMD Acceleration) ===");

		// Check Vector API support
		if (!VectorUtils.isVectorAPISupported()) {
			System.err.println("Vector API is not supported on this platform!");
			System.err.println("Falling back to sequential processing...");
			return processImagesSequential(imagePaths, outputDir, config);
		}

		System.out.println("Vector API supported - using SIMD acceleration");
		System.out.printf("Vector species: %s (length: %d)%n",
				VectorUtils.getOptimalIntSpecies(),
				VectorUtils.getOptimalIntSpecies().length());

		ProcessingStats stats = new ProcessingStats();
		long startTime = System.currentTimeMillis();

		for (Path imagePath : imagePaths) {
			try {
				System.out.printf("Processing: %s [Vector API]%n", imagePath.getFileName());

				// Load image
				BufferedImage image = ImageUtils.loadImage(imagePath);
				if (image == null) {
					System.err.println("Failed to load: " + imagePath);
					stats.failedImages++;
					continue;
				}

				// Apply filters using Vector API
				BufferedImage processed = applyFiltersVector(image, config.getFilters());

				// Resize
				if (config.getTargetWidth() > 0 && config.getTargetHeight() > 0) {
					processed = ImageUtils.resizeImage(processed, config.getTargetWidth(), config.getTargetHeight());
				}

				// Save result
				Path outputPath = outputDir.resolve("vector_" + imagePath.getFileName());
				ImageUtils.saveImage(processed, outputPath, config.getCompressionQuality());

				stats.imagesProcessed++;

			} catch (Exception e) {
				System.err.println("Error processing " + imagePath + ": " + e.getMessage());
				e.printStackTrace();
				stats.failedImages++;
			}
		}

		stats.totalTime = System.currentTimeMillis() - startTime;
		System.out.println("Vector API: " + stats);
		return stats;
	}

	/**
	 * FIXED: Hybrid Vector + Parallel processing with controlled thread pool
	 */
	public static ProcessingStats processImagesVectorParallel(List<Path> imagePaths,
	                                                          Path outputDir,
	                                                          ProcessingConfig config) {
		System.out.println("=== HYBRID VECTOR + PARALLEL PROCESSING (Fixed Thread Pool) ===");

		// Check Vector API support
		if (!VectorUtils.isVectorAPISupported()) {
			System.err.println("Vector API not supported - falling back to standard parallel processing");
			return processImagesParallel(imagePaths, outputDir, config);
		}

		System.out.printf("Using hybrid approach with %d threads and Vector API%n", MAX_THREAD_POOL_SIZE);
		ProcessingStats stats = new ProcessingStats();
		long startTime = System.currentTimeMillis();

		// FIXED: Use controlled ForkJoinPool size
		ForkJoinPool customThreadPool = new ForkJoinPool(MAX_THREAD_POOL_SIZE);

		try {
			// Process in batches to control memory usage
			int batchSize = Math.max(1, MAX_THREAD_POOL_SIZE * 2);
			List<List<Path>> batches = createBatches(imagePaths, batchSize);

			System.out.printf("Processing %d images in %d batches%n", imagePaths.size(), batches.size());

			for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
				List<Path> batch = batches.get(batchIndex);
				System.out.printf("Processing batch %d/%d (%d images)%n",
						batchIndex + 1, batches.size(), batch.size());

				List<CompletableFuture<Boolean>> futures = new ArrayList<>();

				for (Path imagePath : batch) {
					CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
						try {
							System.out.printf("Processing: %s [Thread: %s, Vector API]%n",
									imagePath.getFileName(), Thread.currentThread().getName());

							BufferedImage image = ImageUtils.loadImage(imagePath);
							if (image == null) return false;

							// Use thread-safe Vector API for filter processing
							BufferedImage processed = applyFiltersVectorThreadSafe(image, config.getFilters());

							if (config.getTargetWidth() > 0 && config.getTargetHeight() > 0) {
								processed = ImageUtils.resizeImage(processed, config.getTargetWidth(), config.getTargetHeight());
							}

							Path outputPath = outputDir.resolve("hybrid_" + imagePath.getFileName());
							ImageUtils.saveImage(processed, outputPath, config.getCompressionQuality());

							return true;
						} catch (OutOfMemoryError e) {
							System.err.println("Out of memory processing " + imagePath + ": " + e.getMessage());
							System.gc();
							return false;
						} catch (Exception e) {
							System.err.println("Error processing " + imagePath + ": " + e.getMessage());
							return false;
						}
					}, customThreadPool);

					futures.add(future);
				}

				// Collect batch results
				for (CompletableFuture<Boolean> future : futures) {
					try {
						Boolean result = future.get(90, TimeUnit.SECONDS);
						if (result) {
							stats.imagesProcessed++;
						} else {
							stats.failedImages++;
						}
					} catch (TimeoutException e) {
						System.err.println("Vector processing task timed out");
						future.cancel(true);
						stats.failedImages++;
					} catch (Exception e) {
						System.err.println("Vector processing task failed: " + e.getMessage());
						stats.failedImages++;
					}
				}

				// Clear futures and force GC between batches
				futures.clear();
				if (batchIndex < batches.size() - 1) {
					System.gc();
					Thread.yield();
				}
			}

		} finally {
			// Proper cleanup
			customThreadPool.shutdown();
			try {
				if (!customThreadPool.awaitTermination(60, TimeUnit.SECONDS)) {
					customThreadPool.shutdownNow();
				}
			} catch (InterruptedException e) {
				customThreadPool.shutdownNow();
				Thread.currentThread().interrupt();
			}

			// Clean up thread-local resources
			cleanupThreadLocalResources();
		}

		stats.totalTime = System.currentTimeMillis() - startTime;
		System.out.println("Hybrid Vector+Parallel (Fixed Pool): " + stats);
		return stats;
	}

	/**
	 * HELPER: Create batches of images for controlled processing
	 */
	private static List<List<Path>> createBatches(List<Path> imagePaths, int batchSize) {
		List<List<Path>> batches = new ArrayList<>();
		for (int i = 0; i < imagePaths.size(); i += batchSize) {
			int end = Math.min(i + batchSize, imagePaths.size());
			batches.add(new ArrayList<>(imagePaths.subList(i, end)));
		}
		return batches;
	}

	/**
	 * Apply filters sequentially to an image
	 */
	public static BufferedImage applyFiltersSequential(BufferedImage image, List<FilterType> filters) {
		BufferedImage result = ImageUtils.deepCopy(image);

		for (FilterType filter : filters) {
			result = ImageUtils.applyFilter(result, filter);
		}

		return result;
	}

	/**
	 * Apply filters using Vector API for SIMD acceleration
	 */
	public static BufferedImage applyFiltersVector(BufferedImage image, List<FilterType> filters) {
		BufferedImage result = ImageUtils.deepCopy(image);

		for (FilterType filter : filters) {
			result = applyFilterVector(result, filter);
		}

		return result;
	}

	/**
	 * Thread-safe Vector API filter application
	 */
	private static BufferedImage applyFiltersVectorThreadSafe(BufferedImage image, List<FilterType> filters) {
		BufferedImage result = ImageUtils.deepCopy(image);

		for (FilterType filter : filters) {
			result = applyFilterVectorThreadSafe(result, filter);
		}

		return result;
	}

	/**
	 * Tile-level parallelism using ForkJoinPool
	 */
	private static BufferedImage applyFiltersParallelTiles(BufferedImage image, List<FilterType> filters) {
		if (image.getWidth() < TILE_SIZE * 2 || image.getHeight() < TILE_SIZE * 2) {
			// Image too small for tiling, process sequentially
			return applyFiltersSequential(image, filters);
		}

		// Use limited thread pool for tile processing too
		ForkJoinPool forkJoinPool = new ForkJoinPool(MAX_THREAD_POOL_SIZE);
		try {
			BufferedImage result = ImageUtils.deepCopy(image);

			for (FilterType filter : filters) {
				result = forkJoinPool.invoke(new TileProcessingTask(result, filter,
						0, 0, result.getWidth(), result.getHeight()));
			}

			return result;
		} finally {
			forkJoinPool.shutdown();
		}
	}

	/**
	 * Vector API filter application using VectorUtils
	 */
	private static BufferedImage applyFilterVector(BufferedImage image, FilterType filter) {
		BufferedImage result = ImageUtils.createCompatibleImage(image);
		int totalPixels = image.getWidth() * image.getHeight();
		int[] srcPixels = new int[totalPixels];
		int[] dstPixels = new int[totalPixels];

		ImageUtils.getRGBArray(image, srcPixels);

		switch (filter) {
			case BRIGHTNESS:
				VectorUtils.adjustBrightness(srcPixels, dstPixels, 30);
				break;
			case CONTRAST:
				VectorUtils.adjustContrast(srcPixels, dstPixels, 1.2f);
				break;
			case GRAYSCALE:
				VectorUtils.convertToGrayscale(srcPixels, dstPixels);
				break;
			case BLUR:
				float[] blurKernel = {1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f};
				VectorUtils.applyConvolutionVector(image, result, blurKernel, 3);
				return result;
			case SHARPEN:
				float[] sharpenKernel = {0, -1, 0, -1, 5, -1, 0, -1, 0};
				VectorUtils.applyConvolutionVector(image, result, sharpenKernel, 3);
				return result;
			default:
				return ImageUtils.deepCopy(image);
		}

		ImageUtils.setRGBArray(result, dstPixels);
		return result;
	}

	/**
	 * Thread-safe Vector API filter application with thread-local buffers
	 */
	private static BufferedImage applyFilterVectorThreadSafe(BufferedImage image, FilterType filter) {
		BufferedImage result = ImageUtils.createCompatibleImage(image);
		int totalPixels = image.getWidth() * image.getHeight();

		// Use thread-local buffers to avoid conflicts
		int[] srcPixels = getThreadLocalSrcBuffer(totalPixels);
		int[] dstPixels = getThreadLocalDstBuffer(totalPixels);

		ImageUtils.getRGBArray(image, srcPixels);

		// Apply filter with thread safety considerations
		try {
			switch (filter) {
				case BRIGHTNESS:
					VectorUtils.adjustBrightness(srcPixels, dstPixels, 30);
					break;
				case CONTRAST:
					VectorUtils.adjustContrast(srcPixels, dstPixels, 1.2f);
					break;
				case GRAYSCALE:
					VectorUtils.convertToGrayscale(srcPixels, dstPixels);
					break;
				case BLUR:
					// For convolution operations, use synchronization if needed
					synchronized (VECTOR_LOCK) {
						float[] blurKernel = {1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f};
						VectorUtils.applyConvolutionVector(image, result, blurKernel, 3);
					}
					return result;
				case SHARPEN:
					synchronized (VECTOR_LOCK) {
						float[] sharpenKernel = {0, -1, 0, -1, 5, -1, 0, -1, 0};
						VectorUtils.applyConvolutionVector(image, result, sharpenKernel, 3);
					}
					return result;
				default:
					return ImageUtils.deepCopy(image);
			}

			ImageUtils.setRGBArray(result, dstPixels);
			return result;

		} catch (Exception e) {
			System.err.println("Vector operation failed, falling back to sequential: " + e.getMessage());
			// Fallback to sequential processing
			return ImageUtils.applyFilter(ImageUtils.deepCopy(image), filter);
		}
	}

	/**
	 * Get thread-local source buffer, resize if needed
	 */
	private static int[] getThreadLocalSrcBuffer(int requiredSize) {
		int[] buffer = THREAD_LOCAL_SRC_BUFFER.get();
		if (buffer.length < requiredSize) {
			buffer = new int[Math.max(requiredSize, buffer.length * 2)];
			THREAD_LOCAL_SRC_BUFFER.set(buffer);
		}
		return buffer;
	}

	/**
	 * Get thread-local destination buffer, resize if needed
	 */
	private static int[] getThreadLocalDstBuffer(int requiredSize) {
		int[] buffer = THREAD_LOCAL_DST_BUFFER.get();
		if (buffer.length < requiredSize) {
			buffer = new int[Math.max(requiredSize, buffer.length * 2)];
			THREAD_LOCAL_DST_BUFFER.set(buffer);
		}
		return buffer;
	}

	/**
	 * Clean up thread-local resources
	 */
	private static void cleanupThreadLocalResources() {
		try {
			THREAD_LOCAL_SRC_BUFFER.remove();
			THREAD_LOCAL_DST_BUFFER.remove();
		} catch (Exception e) {
			System.err.println("Warning: Failed to clean up thread-local resources: " + e.getMessage());
		}
	}
}