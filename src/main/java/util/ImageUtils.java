package util;

import model.FilterType;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Utility class for image operations
 */
public class ImageUtils {

	private static final String[] SUPPORTED_FORMATS = {"jpg", "jpeg", "png", "bmp"};

	/**
	 * Load an image from a file path
	 */
	public static BufferedImage loadImage(Path imagePath) throws IOException {
		return ImageIO.read(imagePath.toFile());
	}

	/**
	 * Save an image to a file path
	 */
	public static void saveImage(BufferedImage image, Path outputPath, float quality) throws IOException {
		String format = getFileExtension(outputPath).toLowerCase();
		if (!Arrays.asList(SUPPORTED_FORMATS).contains(format)) {
			format = "jpg";
		}

		ImageIO.write(image, format, outputPath.toFile());
	}

	/**
	 * Apply a filter to an image
	 */
	public static BufferedImage applyFilter(BufferedImage image, FilterType filter) {
		int width = image.getWidth();
		int height = image.getHeight();
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		switch (filter) {
			case GRAYSCALE:
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						int rgb = image.getRGB(x, y);
						int r = (rgb >> 16) & 0xFF;
						int g = (rgb >> 8) & 0xFF;
						int b = rgb & 0xFF;
						int gray = (int)(0.299 * r + 0.587 * g + 0.114 * b);
						int grayRgb = (gray << 16) | (gray << 8) | gray;
						result.setRGB(x, y, grayRgb);
					}
				}
				break;

			case BLUR:
				// Simple 3x3 blur kernel
				float[] kernel = {1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f, 1/9f};
				result = applyConvolution(image, kernel, 3);
				break;

			case SHARPEN:
				// Sharpening kernel
				float[] sharpenKernel = {0, -1, 0, -1, 5, -1, 0, -1, 0};
				result = applyConvolution(image, sharpenKernel, 3);
				break;

			case BRIGHTNESS:
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						int rgb = image.getRGB(x, y);
						int r = Math.min(255, ((rgb >> 16) & 0xFF) + 30);
						int g = Math.min(255, ((rgb >> 8) & 0xFF) + 30);
						int b = Math.min(255, (rgb & 0xFF) + 30);
						result.setRGB(x, y, (r << 16) | (g << 8) | b);
					}
				}
				break;

			case CONTRAST:
				double factor = 1.5;
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						int rgb = image.getRGB(x, y);
						int r = (int)Math.min(255, Math.max(0, factor * ((rgb >> 16) & 0xFF)));
						int g = (int)Math.min(255, Math.max(0, factor * ((rgb >> 8) & 0xFF)));
						int b = (int)Math.min(255, Math.max(0, factor * (rgb & 0xFF)));
						result.setRGB(x, y, (r << 16) | (g << 8) | b);
					}
				}
				break;

			default:
				return deepCopy(image);
		}

		return result;
	}

	/**
	 * Apply convolution filter to image
	 */
	private static BufferedImage applyConvolution(BufferedImage image, float[] kernel, int kernelSize) {
		int width = image.getWidth();
		int height = image.getHeight();
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		int offset = kernelSize / 2;

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				float r = 0, g = 0, b = 0;

				for (int ky = 0; ky < kernelSize; ky++) {
					for (int kx = 0; kx < kernelSize; kx++) {
						int px = Math.min(Math.max(x + kx - offset, 0), width - 1);
						int py = Math.min(Math.max(y + ky - offset, 0), height - 1);

						int rgb = image.getRGB(px, py);
						float weight = kernel[ky * kernelSize + kx];

						r += weight * ((rgb >> 16) & 0xFF);
						g += weight * ((rgb >> 8) & 0xFF);
						b += weight * (rgb & 0xFF);
					}
				}

				int finalR = Math.min(255, Math.max(0, (int)r));
				int finalG = Math.min(255, Math.max(0, (int)g));
				int finalB = Math.min(255, Math.max(0, (int)b));

				result.setRGB(x, y, (finalR << 16) | (finalG << 8) | finalB);
			}
		}

		return result;
	}

	/**
	 * Resize an image to target dimensions
	 */
	public static BufferedImage resizeImage(BufferedImage image, int targetWidth, int targetHeight) {
		BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = resized.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(image, 0, 0, targetWidth, targetHeight, null);
		g.dispose();
		return resized;
	}

	/**
	 * Create a deep copy of an image
	 */
	public static BufferedImage deepCopy(BufferedImage image) {
		BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
		Graphics2D g = copy.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return copy;
	}

	/**
	 * Get file extension from path
	 */
	public static String getFileExtension(Path path) {
		String fileName = path.getFileName().toString();
		int dotIndex = fileName.lastIndexOf('.');
		return dotIndex > 0 ? fileName.substring(dotIndex + 1) : "jpg";
	}

	/**
	 * Get supported image formats
	 */
	public static String[] getSupportedFormats() {
		return SUPPORTED_FORMATS.clone();
	}

	/**
	 * Vector-optimized batch pixel operations
	 */
	public static void getRGBArray(BufferedImage image, int[] pixels) {
		image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
	}

	public static void setRGBArray(BufferedImage image, int[] pixels) {
		image.setRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
	}

	/**
	 * Create image with proper alpha channel support for vector operations
	 */
	public static BufferedImage createCompatibleImage(BufferedImage source) {
		// Use TYPE_INT_ARGB for better vector API compatibility
		if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
			return new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		}
		return new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
	}

	/**
	 * Convert image to ARGB format for vector processing
	 */
	public static BufferedImage ensureARGB(BufferedImage image) {
		if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
			return image;
		}
		BufferedImage argbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = argbImage.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return argbImage;
	}
}