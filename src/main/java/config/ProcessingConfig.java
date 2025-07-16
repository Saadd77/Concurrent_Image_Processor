package config;

import model.FilterType;
import java.util.ArrayList;
import java.util.List;

/**
 * Image processing task configuration
 */
public class ProcessingConfig {
	private final List<FilterType> filters;
	private final int targetWidth;
	private final int targetHeight;
	private final float compressionQuality;
	private final boolean useTileParallelism;

	public ProcessingConfig(List<FilterType> filters, int targetWidth, int targetHeight,
	                        float compressionQuality, boolean useTileParallelism) {
		this.filters = new ArrayList<>(filters);
		this.targetWidth = targetWidth;
		this.targetHeight = targetHeight;
		this.compressionQuality = Math.max(0.1f, Math.min(1.0f, compressionQuality));
		this.useTileParallelism = useTileParallelism;
	}

	// Getters
	public List<FilterType> getFilters() {
		return new ArrayList<>(filters);
	}

	public int getTargetWidth() {
		return targetWidth;
	}

	public int getTargetHeight() {
		return targetHeight;
	}

	public float getCompressionQuality() {
		return compressionQuality;
	}

	public boolean isUseTileParallelism() {
		return useTileParallelism;
	}
}