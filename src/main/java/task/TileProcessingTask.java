package task;

import model.FilterType;
import util.ImageUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.RecursiveTask;

/**
 * ForkJoin task for tile-based processing
 */
public class TileProcessingTask extends RecursiveTask<BufferedImage> {
	private static final int TILE_SIZE = 256;

	private final BufferedImage image;
	private final FilterType filter;
	private final int x, y, width, height;

	public TileProcessingTask(BufferedImage image, FilterType filter,
	                          int x, int y, int width, int height) {
		this.image = image;
		this.filter = filter;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	@Override
	protected BufferedImage compute() {
		if (width <= TILE_SIZE || height <= TILE_SIZE) {
			// Base case: process tile directly
			return processTile(image, filter, x, y, width, height);
		}

		// Divide into quadrants
		int midX = width / 2;
		int midY = height / 2;

		TileProcessingTask topLeft = new TileProcessingTask(image, filter, x, y, midX, midY);
		TileProcessingTask topRight = new TileProcessingTask(image, filter, x + midX, y, width - midX, midY);
		TileProcessingTask bottomLeft = new TileProcessingTask(image, filter, x, y + midY, midX, height - midY);
		TileProcessingTask bottomRight = new TileProcessingTask(image, filter, x + midX, y + midY, width - midX, height - midY);

		// Fork subtasks
		topLeft.fork();
		topRight.fork();
		bottomLeft.fork();

		// Compute one directly and join others
		BufferedImage brResult = bottomRight.compute();
		BufferedImage blResult = bottomLeft.join();
		BufferedImage trResult = topRight.join();
		BufferedImage tlResult = topLeft.join();

		// Combine results
		return combineResults(tlResult, trResult, blResult, brResult);
	}

	private BufferedImage processTile(BufferedImage image, FilterType filter,
	                                  int x, int y, int width, int height) {
		BufferedImage tile = image.getSubimage(x, y, width, height);
		return ImageUtils.applyFilter(tile, filter);
	}

	private BufferedImage combineResults(BufferedImage topLeft, BufferedImage topRight,
	                                     BufferedImage bottomLeft, BufferedImage bottomRight) {
		int totalWidth = topLeft.getWidth() + topRight.getWidth();
		int totalHeight = topLeft.getHeight() + bottomLeft.getHeight();

		BufferedImage combined = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = combined.createGraphics();

		g.drawImage(topLeft, 0, 0, null);
		g.drawImage(topRight, topLeft.getWidth(), 0, null);
		g.drawImage(bottomLeft, 0, topLeft.getHeight(), null);
		g.drawImage(bottomRight, topLeft.getWidth(), topLeft.getHeight(), null);

		g.dispose();
		return combined;
	}
}