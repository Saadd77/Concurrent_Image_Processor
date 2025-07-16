package util;

import jdk.incubator.vector.*;
import java.awt.image.BufferedImage;

/**
 * Thread-safe utility class for Vector API operations
 * Fixed issues with thread safety and proper exception handling
 */
public class VectorUtils {

	// Thread-safe: these are immutable once initialized
	public static final VectorSpecies<Integer> INT_SPECIES;
	public static final VectorSpecies<Float> FLOAT_SPECIES;

	// Initialize species safely with fallback
	static {
		VectorSpecies<Integer> intSpecies;
		VectorSpecies<Float> floatSpecies;

		try {
			intSpecies = IntVector.SPECIES_PREFERRED;
			floatSpecies = FloatVector.SPECIES_PREFERRED;
		} catch (Exception e) {
			// Fallback to a basic species if preferred fails
			intSpecies = IntVector.SPECIES_128;
			floatSpecies = FloatVector.SPECIES_128;
		}

		INT_SPECIES = intSpecies;
		FLOAT_SPECIES = floatSpecies;
	}

	// Color component masks and shifts
	public static final int ALPHA_MASK = 0xFF000000;
	public static final int RED_MASK = 0x00FF0000;
	public static final int GREEN_MASK = 0x0000FF00;
	public static final int BLUE_MASK = 0x000000FF;

	public static final int ALPHA_SHIFT = 24;
	public static final int RED_SHIFT = 16;
	public static final int GREEN_SHIFT = 8;
	public static final int BLUE_SHIFT = 0;

	// Grayscale weights (fixed-point arithmetic)
	public static final int RED_WEIGHT = 77;    // 0.299 * 256
	public static final int GREEN_WEIGHT = 150; // 0.587 * 256
	public static final int BLUE_WEIGHT = 29;   // 0.114 * 256

	/**
	 * Thread-safe color component extraction
	 */
	public static class ColorComponents {
		public final IntVector red, green, blue, alpha;

		public ColorComponents(IntVector pixels) {
			// Use thread-safe vector operations
			this.alpha = pixels.lanewise(VectorOperators.LSHR, ALPHA_SHIFT).lanewise(VectorOperators.AND, 0xFF);
			this.red = pixels.lanewise(VectorOperators.LSHR, RED_SHIFT).lanewise(VectorOperators.AND, 0xFF);
			this.green = pixels.lanewise(VectorOperators.LSHR, GREEN_SHIFT).lanewise(VectorOperators.AND, 0xFF);
			this.blue = pixels.lanewise(VectorOperators.AND, 0xFF);
		}

		public IntVector combine() {
			return alpha.lanewise(VectorOperators.LSHL, ALPHA_SHIFT)
					.or(red.lanewise(VectorOperators.LSHL, RED_SHIFT))
					.or(green.lanewise(VectorOperators.LSHL, GREEN_SHIFT))
					.or(blue);
		}
	}

	/**
	 * Thread-safe clamp operation
	 */
	public static IntVector clamp(IntVector values) {
		return values.lanewise(VectorOperators.MAX, 0).lanewise(VectorOperators.MIN, 255);
	}

	/**
	 * Thread-safe brightness adjustment with proper error handling
	 */
	public static void adjustBrightness(int[] src, int[] dst, int brightness) {
		if (src == null || dst == null) {
			throw new IllegalArgumentException("Source and destination arrays cannot be null");
		}
		if (src.length != dst.length) {
			throw new IllegalArgumentException("Source and destination arrays must have same length");
		}

		int length = src.length;
		int i = 0;

		try {
			// Process in vector chunks
			for (; i < INT_SPECIES.loopBound(length); i += INT_SPECIES.length()) {
				IntVector pixels = IntVector.fromArray(INT_SPECIES, src, i);
				ColorComponents components = new ColorComponents(pixels);

				IntVector newRed = clamp(components.red.add(brightness));
				IntVector newGreen = clamp(components.green.add(brightness));
				IntVector newBlue = clamp(components.blue.add(brightness));

				IntVector result = components.alpha.lanewise(VectorOperators.LSHL, ALPHA_SHIFT)
						.or(newRed.lanewise(VectorOperators.LSHL, RED_SHIFT))
						.or(newGreen.lanewise(VectorOperators.LSHL, GREEN_SHIFT))
						.or(newBlue);

				result.intoArray(dst, i);
			}
		} catch (Exception e) {
			// If vector operations fail, fall back to scalar processing
			System.err.println("Vector operation failed, falling back to scalar: " + e.getMessage());
			i = 0; // Reset to process everything scalar
		}

		// Handle remaining elements with scalar operations
		for (; i < length; i++) {
			int pixel = src[i];
			int a = (pixel >> 24) & 0xFF;
			int r = Math.max(0, Math.min(255, ((pixel >> 16) & 0xFF) + brightness));
			int g = Math.max(0, Math.min(255, ((pixel >> 8) & 0xFF) + brightness));
			int b = Math.max(0, Math.min(255, (pixel & 0xFF) + brightness));
			dst[i] = (a << 24) | (r << 16) | (g << 8) | b;
		}
	}

	/**
	 * Thread-safe contrast adjustment with proper error handling
	 */
	public static void adjustContrast(int[] src, int[] dst, float contrast) {
		if (src == null || dst == null) {
			throw new IllegalArgumentException("Source and destination arrays cannot be null");
		}
		if (src.length != dst.length) {
			throw new IllegalArgumentException("Source and destination arrays must have same length");
		}

		int length = src.length;
		int i = 0;
		int contrastInt = (int)(contrast * 256); // Fixed-point arithmetic

		try {
			for (; i < INT_SPECIES.loopBound(length); i += INT_SPECIES.length()) {
				IntVector pixels = IntVector.fromArray(INT_SPECIES, src, i);
				ColorComponents components = new ColorComponents(pixels);

				// Apply contrast: newValue = ((oldValue - 128) * contrast) + 128
				IntVector newRed = clamp(components.red.sub(128).mul(contrastInt).lanewise(VectorOperators.LSHR, 8).add(128));
				IntVector newGreen = clamp(components.green.sub(128).mul(contrastInt).lanewise(VectorOperators.LSHR, 8).add(128));
				IntVector newBlue = clamp(components.blue.sub(128).mul(contrastInt).lanewise(VectorOperators.LSHR, 8).add(128));

				IntVector result = components.alpha.lanewise(VectorOperators.LSHL, ALPHA_SHIFT)
						.or(newRed.lanewise(VectorOperators.LSHL, RED_SHIFT))
						.or(newGreen.lanewise(VectorOperators.LSHL, GREEN_SHIFT))
						.or(newBlue);

				result.intoArray(dst, i);
			}
		} catch (Exception e) {
			System.err.println("Vector contrast operation failed, falling back to scalar: " + e.getMessage());
			i = 0;
		}

		// Handle remaining elements
		for (; i < length; i++) {
			int pixel = src[i];
			int a = (pixel >> 24) & 0xFF;
			int r = (int)Math.max(0, Math.min(255, ((((pixel >> 16) & 0xFF) - 128) * contrast) + 128));
			int g = (int)Math.max(0, Math.min(255, ((((pixel >> 8) & 0xFF) - 128) * contrast) + 128));
			int b = (int)Math.max(0, Math.min(255, (((pixel & 0xFF) - 128) * contrast) + 128));
			dst[i] = (a << 24) | (r << 16) | (g << 8) | b;
		}
	}

	/**
	 * Thread-safe grayscale conversion with proper error handling
	 */
	public static void convertToGrayscale(int[] src, int[] dst) {
		if (src == null || dst == null) {
			throw new IllegalArgumentException("Source and destination arrays cannot be null");
		}
		if (src.length != dst.length) {
			throw new IllegalArgumentException("Source and destination arrays must have same length");
		}

		int length = src.length;
		int i = 0;

		try {
			for (; i < INT_SPECIES.loopBound(length); i += INT_SPECIES.length()) {
				IntVector pixels = IntVector.fromArray(INT_SPECIES, src, i);
				ColorComponents components = new ColorComponents(pixels);

				// Calculate grayscale value using fixed-point arithmetic
				IntVector gray = components.red.mul(RED_WEIGHT)
						.add(components.green.mul(GREEN_WEIGHT))
						.add(components.blue.mul(BLUE_WEIGHT))
						.lanewise(VectorOperators.LSHR, 8);

				IntVector result = components.alpha.lanewise(VectorOperators.LSHL, ALPHA_SHIFT)
						.or(gray.lanewise(VectorOperators.LSHL, RED_SHIFT))
						.or(gray.lanewise(VectorOperators.LSHL, GREEN_SHIFT))
						.or(gray);

				result.intoArray(dst, i);
			}
		} catch (Exception e) {
			System.err.println("Vector grayscale operation failed, falling back to scalar: " + e.getMessage());
			i = 0;
		}

		// Handle remaining elements
		for (; i < length; i++) {
			int pixel = src[i];
			int a = (pixel >> 24) & 0xFF;
			int r = (pixel >> 16) & 0xFF;
			int g = (pixel >> 8) & 0xFF;
			int b = pixel & 0xFF;
			int gray = (int)((r * 0.299) + (g * 0.587) + (b * 0.114));
			dst[i] = (a << 24) | (gray << 16) | (gray << 8) | gray;
		}
	}

	/**
	 * Thread-safe convolution with proper synchronization for BufferedImage access
	 */
	public static synchronized void applyConvolutionVector(BufferedImage src, BufferedImage dst, float[] kernel, int kernelSize) {
		if (src == null || dst == null || kernel == null) {
			throw new IllegalArgumentException("Parameters cannot be null");
		}

		int width = src.getWidth();
		int height = src.getHeight();
		int offset = kernelSize / 2;

		// Create local arrays to avoid thread contention on BufferedImage
		int[] srcPixels = new int[width * height];
		int[] dstPixels = new int[width * height];

		try {
			src.getRGB(0, 0, width, height, srcPixels, 0, width);

			// Process each pixel
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					float r = 0, g = 0, b = 0;
					int a = 0;

					// Apply kernel
					for (int ky = 0; ky < kernelSize; ky++) {
						for (int kx = 0; kx < kernelSize; kx++) {
							int px = Math.min(Math.max(x + kx - offset, 0), width - 1);
							int py = Math.min(Math.max(y + ky - offset, 0), height - 1);

							int pixel = srcPixels[py * width + px];
							float weight = kernel[ky * kernelSize + kx];

							if (kx == offset && ky == offset) {
								a = (pixel >> 24) & 0xFF; // Preserve alpha from center pixel
							}

							r += weight * ((pixel >> 16) & 0xFF);
							g += weight * ((pixel >> 8) & 0xFF);
							b += weight * (pixel & 0xFF);
						}
					}

					int finalR = Math.min(255, Math.max(0, (int)r));
					int finalG = Math.min(255, Math.max(0, (int)g));
					int finalB = Math.min(255, Math.max(0, (int)b));

					dstPixels[y * width + x] = (a << 24) | (finalR << 16) | (finalG << 8) | finalB;
				}
			}

			dst.setRGB(0, 0, width, height, dstPixels, 0, width);

		} catch (Exception e) {
			System.err.println("Convolution operation failed: " + e.getMessage());
			// Create a copy as fallback
			dst.getGraphics().drawImage(src, 0, 0, null);
		}
	}

	/**
	 * Thread-safe Vector API support check
	 */
	public static boolean isVectorAPISupported() {
		try {
			// Try to create a simple vector to test support
			IntVector.zero(INT_SPECIES);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Get optimal vector species for current platform
	 */
	public static VectorSpecies<Integer> getOptimalIntSpecies() {
		return INT_SPECIES;
	}

	/**
	 * Thread-safe chunk size calculation
	 */
	public static int getOptimalChunkSize(int totalPixels) {
		int vectorLength = INT_SPECIES.length();
		int chunks = totalPixels / vectorLength;

		// Aim for chunks that are multiples of vector length
		if (chunks < 64) return Math.max(vectorLength * 4, totalPixels);
		if (chunks < 256) return vectorLength * 16;
		return vectorLength * 64;
	}
}