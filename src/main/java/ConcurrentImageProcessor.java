import config.ProcessingConfig;
import model.FilterType;
import model.ProcessingStats;
import processor.ImageProcessor;
import util.ImageUtils;
import util.VectorUtils;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application class for the Concurrent Image Processor
 * Provides interactive menu system and orchestrates image processing operations
 * Now includes Vector API SIMD acceleration support with controlled thread pool management
 */2
public class ConcurrentImageProcessor {

	private static final Logger LOGGER = Logger.getLogger(ConcurrentImageProcessor.class.getName());
	// FIXED: Use same thread pool size as ImageProcessor for consistency
	private static final int THREAD_POOL_SIZE = 8;
	private static final int MAX_MENU_CHOICE = 7;
	private static final int MIN_MENU_CHOICE = 0;

	// Security: Prevent directory traversal attacks
	private static final String INPUT_DIR_NAME = "input_images";
	private static final String OUTPUT_DIR_NAME = "output_images";

	// Input validation constants - adjusted for memory constraints
	private static final int MAX_IMAGE_DIMENSION = 8000; // Reduced from 10000
	private static final int MIN_HEIGHT = 1;
	private static final long MAX_FILE_SIZE = 25 * 1024 * 1024; // Reduced from 50MB to 25MB

	// Memory management constants
	private static final int RECOMMENDED_BATCH_SIZE = 12; // 2x thread pool size
	private static final long MIN_FREE_MEMORY = 100 * 1024 * 1024; // 100MB minimum

	/**
	 * Display the main interactive menu
	 */
	public static void displayMenu() {
		System.out.println("\n" + "=".repeat(60));
		System.out.println("         CONCURRENT IMAGE PROCESSOR");
		System.out.println("=".repeat(60));
		System.out.println("1. Sequential Processing (Baseline)");
		System.out.println("2. Parallel Processing (Multi-threaded)");
		System.out.println("3. Vector API Processing (SIMD Acceleration)");
		System.out.println("4. Hybrid Vector + Parallel Processing");
		System.out.println("5. Performance Comparison (All Methods)");
		System.out.println("6. Configure Processing Settings");
		System.out.println("7. System Information");
		System.out.println("0. Exit");
		System.out.println("=".repeat(60));
		System.out.print("Choose an option (0-7): ");
	}

	/**
	 * Display system information including Vector API support and memory status
	 */
	public static void displaySystemInfo() {
		System.out.println("\n=== SYSTEM INFORMATION ===");
		System.out.println("Java Version: " + System.getProperty("java.version"));
		System.out.println("Available Processors: " + Runtime.getRuntime().availableProcessors());

		// Memory information
		Runtime runtime = Runtime.getRuntime();
		long maxMemory = runtime.maxMemory();
		long totalMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		long usedMemory = totalMemory - freeMemory;

		System.out.println("Max Memory: " + (maxMemory / 1024 / 1024) + " MB");
		System.out.println("Used Memory: " + (usedMemory / 1024 / 1024) + " MB");
		System.out.println("Free Memory: " + (freeMemory / 1024 / 1024) + " MB");
		System.out.println("Thread Pool Size: " + THREAD_POOL_SIZE + " (Fixed)");
		System.out.println("Recommended Batch Size: " + RECOMMENDED_BATCH_SIZE);

		// Memory status warning
		if (freeMemory < MIN_FREE_MEMORY) {
			System.out.println("⚠️  WARNING: Low memory available - consider reducing image count or size");
		}

		// Vector API Information
		System.out.println("\n--- Vector API Information ---");
		if (VectorUtils.isVectorAPISupported()) {
			System.out.println("✓ Vector API: SUPPORTED");
			System.out.println("Optimal Int Species: " + VectorUtils.getOptimalIntSpecies());
			System.out.println("Vector Length: " + VectorUtils.getOptimalIntSpecies().length());
			System.out.println("SIMD acceleration: AVAILABLE");
		} else {
			System.out.println("✗ Vector API: NOT SUPPORTED");
			System.out.println("SIMD acceleration: NOT AVAILABLE");
			System.out.println("Note: Vector API requires Java 17+ with --add-modules jdk.incubator.vector");
		}

		System.out.println("\n--- Processing Constraints ---");
		System.out.println("Max Image Dimension: " + MAX_IMAGE_DIMENSION + "px");
		System.out.println("Max File Size: " + (MAX_FILE_SIZE / 1024 / 1024) + " MB");
		System.out.println("Supported Formats: " + Arrays.toString(ImageUtils.getSupportedFormats()));
	}

	/**
	 * Configure processing settings through interactive prompts with memory considerations
	 */
	public static ProcessingConfig configureSettings(Scanner scanner) {
		System.out.println("\n=== PROCESSING CONFIGURATION ===");

		// Display memory status first
		Runtime runtime = Runtime.getRuntime();
		long freeMemory = runtime.freeMemory();
		System.out.printf("Available Memory: %d MB%n", freeMemory / 1024 / 1024);

		if (freeMemory < MIN_FREE_MEMORY) {
			System.out.println("⚠️  WARNING: Low memory! Consider using smaller images or fewer filters.");
		}

		// Filter selection with improved validation
		System.out.println("\nAvailable filters:");
		FilterType[] availableFilters = FilterType.values();
		for (int i = 0; i < availableFilters.length; i++) {
			System.out.printf("%d. %s%n", i + 1, availableFilters[i]);
		}
		System.out.print("Select filters (comma-separated numbers, e.g., 1,2,3): ");

		List<FilterType> selectedFilters = parseFilterSelection(scanner.nextLine().trim(), availableFilters);

		// Resize settings with validation and memory considerations
		System.out.print("Target width (0 for no resize, max " + MAX_IMAGE_DIMENSION + "): ");
		int width = getIntInput(scanner, 0, MAX_IMAGE_DIMENSION);

		int height = 0;
		if (width > 0) {
			System.out.print("Target height (min " + MIN_HEIGHT + ", max " + MAX_IMAGE_DIMENSION + "): ");
			height = getIntInput(scanner, MIN_HEIGHT, MAX_IMAGE_DIMENSION);

			// Warn about large dimensions
			if (width > 4000 || height > 4000) {
				System.out.println("⚠️  Large dimensions may consume significant memory with parallel processing.");
			}
		}

		// Compression quality
		System.out.print("Compression quality (0.1-1.0): ");
		float quality = getFloatInput(scanner, 0.1f, 1.0f);

		// Tile parallelism with memory consideration
		System.out.print("Use tile-level parallelism for large images? (y/n): ");
		boolean useTileParallelism = getBooleanInput(scanner);

		if (useTileParallelism) {
			System.out.println("ℹ️  Tile parallelism helps manage memory for large images.");
		}

		return new ProcessingConfig(selectedFilters, width, height, quality, useTileParallelism);
	}

	/**
	 * Parse filter selection with improved error handling
	 */
	private static List<FilterType> parseFilterSelection(String input, FilterType[] availableFilters) {
		List<FilterType> selectedFilters = new ArrayList<>();

		if (input.isEmpty()) {
			selectedFilters = getDefaultFilters();
			System.out.println("No filters selected. Using default: " + selectedFilters);
			return selectedFilters;
		}

		try {
			String[] filterNumbers = input.split(",");
			Set<Integer> uniqueIndices = new HashSet<>(); // Prevent duplicates

			for (String num : filterNumbers) {
				String trimmed = num.trim();
				if (!trimmed.isEmpty()) {
					int index = Integer.parseInt(trimmed) - 1;
					if (index >= 0 && index < availableFilters.length) {
						uniqueIndices.add(index);
					} else {
						LOGGER.warning("Invalid filter index: " + (index + 1));
					}
				}
			}

			for (int index : uniqueIndices) {
				selectedFilters.add(availableFilters[index]);
			}

		} catch (NumberFormatException e) {
			LOGGER.warning("Invalid number format in filter selection: " + e.getMessage());
		}

		if (selectedFilters.isEmpty()) {
			selectedFilters = getDefaultFilters();
			System.out.println("No valid filters selected. Using default: " + selectedFilters);
		}

		return selectedFilters;
	}

	/**
	 * Get default filters configuration (memory-efficient)
	 */
	private static List<FilterType> getDefaultFilters() {
		return Arrays.asList(FilterType.GRAYSCALE, FilterType.BLUR);
	}

	/**
	 * Get valid integer input from user with range validation
	 */
	private static int getIntInput(Scanner scanner, int min, int max) {
		while (true) {
			try {
				String input = scanner.nextLine().trim();
				if (input.isEmpty()) {
					System.out.printf("Please enter a number between %d and %d: ", min, max);
					continue;
				}

				int value = Integer.parseInt(input);
				if (value >= min && value <= max) {
					return value;
				}
				System.out.printf("Please enter a number between %d and %d: ", min, max);
			} catch (NumberFormatException e) {
				System.out.print("Please enter a valid number: ");
			}
		}
	}

	/**
	 * Get valid float input from user within range
	 */
	private static float getFloatInput(Scanner scanner, float min, float max) {
		while (true) {
			try {
				String input = scanner.nextLine().trim();
				if (input.isEmpty()) {
					System.out.printf("Please enter a number between %.1f and %.1f: ", min, max);
					continue;
				}

				float value = Float.parseFloat(input);
				if (value >= min && value <= max && !Float.isNaN(value) && !Float.isInfinite(value)) {
					return value;
				}
				System.out.printf("Please enter a number between %.1f and %.1f: ", min, max);
			} catch (NumberFormatException e) {
				System.out.print("Please enter a valid number: ");
			}
		}
	}

	/**
	 * Get boolean input from user
	 */
	private static boolean getBooleanInput(Scanner scanner) {
		while (true) {
			String input = scanner.nextLine().trim().toLowerCase();
			if (input.startsWith("y") || input.equals("true")) {
				return true;
			} else if (input.startsWith("n") || input.equals("false")) {
				return false;
			}
			System.out.print("Please enter 'y' for yes or 'n' for no: ");
		}
	}

	/**
	 * Display current processing configuration with memory impact
	 */
	public static void displayConfiguration(ProcessingConfig config) {
		System.out.println("\n=== CURRENT CONFIGURATION ===");
		System.out.println("Filters: " + config.getFilters());
		if (config.getTargetWidth() > 0) {
			System.out.printf("Resize: %dx%d%n", config.getTargetWidth(), config.getTargetHeight());

			// Estimate memory usage
			long pixelCount = (long) config.getTargetWidth() * config.getTargetHeight();
			long estimatedMemoryMB = (pixelCount * 4 * THREAD_POOL_SIZE) / (1024 * 1024); // 4 bytes per pixel, threads
			System.out.printf("Estimated Memory per Thread: ~%d MB%n", estimatedMemoryMB);
		} else {
			System.out.println("Resize: No resizing");
		}
		System.out.printf("Compression Quality: %.1f%n", config.getCompressionQuality());
		System.out.println("Tile Parallelism: " + (config.isUseTileParallelism() ? "Enabled" : "Disabled"));
		System.out.printf("Thread Pool Size: %d (Fixed)%n", THREAD_POOL_SIZE);
		System.out.printf("Processing Strategy: Batch processing with controlled thread pool%n");
	}

	/**
	 * Validate and sanitize directory path to prevent directory traversal
	 */
	private static Path validateDirectory(String dirName) throws SecurityException {
		Path currentDir = Paths.get("").toAbsolutePath();
		Path targetDir = currentDir.resolve(dirName).normalize();

		// Security check: ensure the target directory is within current directory
		if (!targetDir.startsWith(currentDir)) {
			throw new SecurityException("Directory traversal attempt detected: " + dirName);
		}

		return targetDir;
	}

	/**
	 * Find and return list of image files with enhanced validation and memory checks
	 */
	private static List<Path> findImageFiles(Path inputDir) {
		List<Path> imagePaths = new ArrayList<>();

		if (!Files.exists(inputDir)) {
			LOGGER.warning("Input directory does not exist: " + inputDir);
			return imagePaths;
		}

		try {
			Files.walkFileTree(inputDir, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					// Security: Check file size to prevent processing huge files
					if (attrs.size() > MAX_FILE_SIZE) {
						LOGGER.warning("Skipping large file: " + file + " (size: " + attrs.size() + " bytes)");
						return FileVisitResult.CONTINUE;
					}

					// Check if it's a supported image format
					String extension = ImageUtils.getFileExtension(file).toLowerCase();
					if (Arrays.asList(ImageUtils.getSupportedFormats()).contains(extension)) {
						imagePaths.add(file);
					}

					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
					LOGGER.warning("Could not access file: " + file + " - " + exc.getMessage());
					return FileVisitResult.CONTINUE;
				}
			});

		} catch (IOException e) {
			LOGGER.severe("Error scanning input directory: " + e.getMessage());
		}

		// Sort for consistent processing order
		imagePaths.sort(Comparator.comparing(Path::getFileName));

		// Memory check and warning
		Runtime runtime = Runtime.getRuntime();
		long freeMemory = runtime.freeMemory();
		int imageCount = imagePaths.size();

		if (imageCount > RECOMMENDED_BATCH_SIZE && freeMemory < MIN_FREE_MEMORY * 2) {
			System.out.printf("⚠️  Found %d images with limited memory. Processing will use batches of %d.%n",
					imageCount, RECOMMENDED_BATCH_SIZE);
		}

		return imagePaths;
	}

	/**
	 * Display comprehensive performance comparison results
	 */
	private static void displayPerformanceComparison(ProcessingStats seqResults,
	                                                 ProcessingStats parResults,
	                                                 ProcessingStats vectorResults,
	                                                 ProcessingStats hybridResults) {
		System.out.println("\n" + "=".repeat(80));
		System.out.println("                    COMPREHENSIVE PERFORMANCE COMPARISON");
		System.out.println("=".repeat(80));

		double seqTimeSeconds = seqResults.totalTime / 1000.0;
		double parTimeSeconds = parResults.totalTime / 1000.0;
		double vectorTimeSeconds = vectorResults.totalTime / 1000.0;
		double hybridTimeSeconds = hybridResults.totalTime / 1000.0;

		System.out.printf("%-25s | %8s | %8s | %8s | %8s%n",
				"Method", "Time (s)", "Speedup", "Images", "Failed");
		System.out.println("-".repeat(80));

		System.out.printf("%-25s | %8.2f | %8s | %8d | %8d%n",
				"Sequential", seqTimeSeconds, "1.0x",
				seqResults.imagesProcessed, seqResults.failedImages);

		System.out.printf("%-25s | %8.2f | %8.1fx | %8d | %8d%n",
				"Parallel (Fixed Pool)", parTimeSeconds, seqTimeSeconds / parTimeSeconds,
				parResults.imagesProcessed, parResults.failedImages);

		System.out.printf("%-25s | %8.2f | %8.1fx | %8d | %8d%n",
				"Vector API", vectorTimeSeconds, seqTimeSeconds / vectorTimeSeconds,
				vectorResults.imagesProcessed, vectorResults.failedImages);

		System.out.printf("%-25s | %8.2f | %8.1fx | %8d | %8d%n",
				"Hybrid (Vec+Par Fixed)", hybridTimeSeconds, seqTimeSeconds / hybridTimeSeconds,
				hybridResults.imagesProcessed, hybridResults.failedImages);

		System.out.println("=".repeat(80));

		// Find the best performing method
		double bestTime = Math.min(Math.min(seqTimeSeconds, parTimeSeconds),
				Math.min(vectorTimeSeconds, hybridTimeSeconds));
		String bestMethod = "";

		if (bestTime == seqTimeSeconds) bestMethod = "Sequential";
		else if (bestTime == parTimeSeconds) bestMethod = "Parallel (Fixed Pool)";
		else if (bestTime == vectorTimeSeconds) bestMethod = "Vector API";
		else bestMethod = "Hybrid Vector+Parallel";

		System.out.printf("🏆 Best Performance: %s (%.2fs)%n", bestMethod, bestTime);

		// Additional insights
		System.out.println("\n--- Performance Insights ---");
		System.out.printf("Thread Pool: Fixed size of %d threads for controlled memory usage%n", THREAD_POOL_SIZE);
		if (vectorTimeSeconds < seqTimeSeconds) {
			System.out.println("✓ Vector API SIMD acceleration is effective");
		}
		if (hybridTimeSeconds < Math.min(parTimeSeconds, vectorTimeSeconds)) {
			System.out.println("✓ Hybrid approach provides best overall performance");
		}
		if (parTimeSeconds < seqTimeSeconds) {
			System.out.printf("✓ Parallel processing provides %.1fx speedup with controlled memory usage%n",
					seqTimeSeconds / parTimeSeconds);
		}

		// Memory efficiency note
		System.out.println("✓ All parallel methods use fixed thread pools to prevent memory exhaustion");
	}

	/**
	 * Create default configuration optimized for memory usage
	 */
	private static ProcessingConfig createDefaultConfiguration() {
		List<FilterType> defaultFilters = Arrays.asList(
				FilterType.GRAYSCALE,
				FilterType.BLUR
		);
		// Conservative default settings for memory efficiency
		return new ProcessingConfig(defaultFilters, 800, 600, 0.8f, true);
	}

	/**
	 * Process images with enhanced error handling and memory monitoring
	 */
	private static void processWithMethod(List<Path> imagePaths, Path outputDir,
	                                      ProcessingConfig config, int methodChoice) {
		if (imagePaths.isEmpty()) {
			System.out.println("No images found to process!");
			return;
		}

		// Pre-processing memory check
		Runtime runtime = Runtime.getRuntime();
		long freeMemoryBefore = runtime.freeMemory();

		System.out.printf("Found %d images to process%n", imagePaths.size());
		System.out.printf("Available memory: %d MB%n", freeMemoryBefore / 1024 / 1024);
		displayConfiguration(config);

		// Suggest garbage collection before processing
		if (freeMemoryBefore < MIN_FREE_MEMORY) {
			System.out.println("Running garbage collection to free memory...");
			System.gc();
			Thread.yield();
		}

		String methodName = getMethodName(methodChoice);
		System.out.printf("\n🚀 Starting %s processing...%n", methodName);

		try {
			ProcessingStats stats;
			switch (methodChoice) {
				case 1:
					stats = ImageProcessor.processImagesSequential(imagePaths, outputDir, config);
					break;
				case 2:
					stats = ImageProcessor.processImagesParallel(imagePaths, outputDir, config);
					break;
				case 3:
					stats = ImageProcessor.processImagesVector(imagePaths, outputDir, config);
					break;
				case 4:
					stats = ImageProcessor.processImagesVectorParallel(imagePaths, outputDir, config);
					break;
				default:
					System.out.println("Invalid processing method!");
					return;
			}

			// Post-processing memory check
			long freeMemoryAfter = runtime.freeMemory();

			System.out.println("\n=== PROCESSING COMPLETE ===");
			System.out.println(stats);
			System.out.println("Output directory: " + outputDir);
			System.out.printf("Memory before: %d MB, after: %d MB%n",
					freeMemoryBefore / 1024 / 1024, freeMemoryAfter / 1024 / 1024);

			if (stats.failedImages > 0) {
				System.out.printf("⚠️  %d images failed to process (possibly due to memory constraints)%n",
						stats.failedImages);
			}

		} catch (OutOfMemoryError e) {
			System.err.println("❌ Out of memory error! Try reducing image count, size, or number of filters.");
			System.err.println("Current configuration may be too demanding for available memory.");
		} catch (Exception e) {
			LOGGER.severe("Error during processing: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Get method name for display
	 */
	private static String getMethodName(int methodChoice) {
		switch (methodChoice) {
			case 1: return "Sequential";
			case 2: return "Parallel (Fixed Pool)";
			case 3: return "Vector API";
			case 4: return "Hybrid Vector+Parallel";
			default: return "Unknown";
		}
	}

	/**
	 * Run comprehensive performance comparison with memory monitoring
	 */
	private static void runPerformanceComparison(List<Path> imagePaths, Path outputDir, ProcessingConfig config) {
		if (imagePaths.isEmpty()) {
			System.out.println("No images found for performance comparison!");
			return;
		}

		// Memory check before comparison
		Runtime runtime = Runtime.getRuntime();
		long totalImages = imagePaths.size();
		long freeMemory = runtime.freeMemory();

		System.out.println("\n=== STARTING COMPREHENSIVE PERFORMANCE COMPARISON ===");
		System.out.printf("Processing %d images with all methods...%n", totalImages);
		System.out.printf("Available memory: %d MB%n", freeMemory / 1024 / 1024);

		if (freeMemory < MIN_FREE_MEMORY && totalImages > 5) {
			System.out.println("⚠️  Warning: Low memory for comparison. Consider reducing image count.");
			System.out.print("Continue anyway? (y/n): ");
			Scanner scanner = new Scanner(System.in);
			if (!getBooleanInput(scanner)) {
				return;
			}
		}

		displayConfiguration(config);

		try {
			// Run all processing methods with memory monitoring
			System.out.println("\n--- Sequential Processing ---");
			ProcessingStats seqStats = ImageProcessor.processImagesSequential(imagePaths, outputDir, config);
			forceGarbageCollection();

			System.out.println("\n--- Parallel Processing (Fixed Pool) ---");
			ProcessingStats parStats = ImageProcessor.processImagesParallel(imagePaths, outputDir, config);
			forceGarbageCollection();

			System.out.println("\n--- Vector API Processing ---");
			ProcessingStats vectorStats = ImageProcessor.processImagesVector(imagePaths, outputDir, config);
			forceGarbageCollection();

			System.out.println("\n--- Hybrid Vector+Parallel Processing ---");
			ProcessingStats hybridStats = ImageProcessor.processImagesVectorParallel(imagePaths, outputDir, config);
			forceGarbageCollection();

			// Display comprehensive comparison
			displayPerformanceComparison(seqStats, parStats, vectorStats, hybridStats);

		} catch (OutOfMemoryError e) {
			System.err.println("❌ Out of memory during performance comparison!");
			System.err.println("Try reducing the number of images or their dimensions.");
		} catch (Exception e) {
			LOGGER.severe("Error during performance comparison: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Force garbage collection and give it time to work
	 */
	private static void forceGarbageCollection() {
		System.gc();
		try {
			Thread.sleep(1000); // Give GC time to work
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Setup directories and validate environment
	 */
	private static boolean setupEnvironment() {
		try {
			// Validate and create directories
			Path inputDir = validateDirectory(INPUT_DIR_NAME);
			Path outputDir = validateDirectory(OUTPUT_DIR_NAME);

			if (!Files.exists(inputDir)) {
				Files.createDirectories(inputDir);
				System.out.println("Created input directory: " + inputDir);
				System.out.println("Please place your images in: " + inputDir);
			}

			if (!Files.exists(outputDir)) {
				Files.createDirectories(outputDir);
				System.out.println("Created output directory: " + outputDir);
			}

			return true;

		} catch (Exception e) {
			LOGGER.severe("Failed to setup environment: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Get valid menu choice from user
	 */
	private static int getMenuChoice(Scanner scanner) {
		while (true) {
			try {
				String input = scanner.nextLine().trim();
				if (input.isEmpty()) {
					System.out.print("Please enter your choice (0-7): ");
					continue;
				}

				int choice = Integer.parseInt(input);
				if (choice >= MIN_MENU_CHOICE && choice <= MAX_MENU_CHOICE) {
					return choice;
				}
				System.out.printf("Please enter a number between %d and %d: ", MIN_MENU_CHOICE, MAX_MENU_CHOICE);
			} catch (NumberFormatException e) {
				System.out.print("Please enter a valid number: ");
			}
		}
	}

	/**
	 * Main method - Entry point for the application
	 */
	public static void main(String[] args) {
		System.out.println("🚀 Starting Concurrent Image Processor with Fixed Thread Pool Management...");
		System.out.printf("Using %d threads for optimal memory usage%n", THREAD_POOL_SIZE);

		// Setup environment and validate directories
		if (!setupEnvironment()) {
			System.err.println("❌ Failed to setup environment. Exiting...");
			System.exit(1);
		}

		// Initialize default configuration (memory-efficient)
		ProcessingConfig currentConfig = createDefaultConfiguration();
		Scanner scanner = new Scanner(System.in);

		// Validate paths
		Path inputDir;
		Path outputDir;
		try {
			inputDir = validateDirectory(INPUT_DIR_NAME);
			outputDir = validateDirectory(OUTPUT_DIR_NAME);
		} catch (SecurityException e) {
			System.err.println("❌ Security error: " + e.getMessage());
			return;
		}

		// Display initial system info
		System.out.printf("Fixed thread pool size: %d threads%n", THREAD_POOL_SIZE);
		System.out.printf("Available memory: %d MB%n", Runtime.getRuntime().freeMemory() / 1024 / 1024);

		// Main application loop
		boolean running = true;
		while (running) {
			try {
				displayMenu();
				int choice = getMenuChoice(scanner);

				switch (choice) {
					case 1: // Sequential Processing
					case 2: // Parallel Processing
					case 3: // Vector API Processing
					case 4: // Hybrid Processing
						List<Path> imagePaths = findImageFiles(inputDir);
						processWithMethod(imagePaths, outputDir, currentConfig, choice);
						break;

					case 5: // Performance Comparison
						List<Path> comparisonImages = findImageFiles(inputDir);
						runPerformanceComparison(comparisonImages, outputDir, currentConfig);
						break;

					case 6: // Configure Settings
						currentConfig = configureSettings(scanner);
						System.out.println("✓ Configuration updated successfully!");
						displayConfiguration(currentConfig);
						break;

					case 7: // System Information
						displaySystemInfo();
						break;

					case 0: // Exit
						System.out.println("👋 Thank you for using Concurrent Image Processor!");
						running = false;
						break;

					default:
						System.out.println("❌ Invalid choice. Please try again.");
				}

				if (running && choice != 0) {
					// Force garbage collection between operations
					System.gc();
					System.out.println("\nPress Enter to continue...");
					scanner.nextLine();
				}

			} catch (OutOfMemoryError e) {
				System.err.println("❌ Out of memory error in main loop!");
				System.err.println("Forcing garbage collection...");
				System.gc();
				try {
					Thread.sleep(2000);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
				}
				System.out.println("Press Enter to continue...");
				scanner.nextLine();
			} catch (Exception e) {
				LOGGER.severe("Unexpected error in main loop: " + e.getMessage());
				System.err.println("❌ An unexpected error occurred: " + e.getMessage());
				System.out.println("Press Enter to continue...");
				scanner.nextLine();

			}
		}

		scanner.close();
		System.out.println("🎯 Application terminated successfully.");
	}
}