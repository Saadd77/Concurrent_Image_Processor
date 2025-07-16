# ConcurrentImageProcessor

A Java application for concurrent image processing with multi-threading capabilities.

## Features

- **Concurrent Processing**: Multi-threaded image processing for improved performance
- **Filter Support**: Multiple image filters and transformations
- **Tile-based Processing**: Efficient processing of large images through tiling
- **Flexible Configuration**: Configurable processing parameters
- **Docker Support**: Containerized deployment ready

## Project Structure

```
ConcurrentImageProcessor/
├── src/
│   └── main/
│       └── java/
│           ├── config/
│           │   └── ProcessingConfig.java
│           ├── model/
│           │   ├── FilterType.java
│           │   └── ProcessingStats.java
│           ├── processor/
│           │   └── ImageProcessor.java
│           ├── task/
│           │   └── TileProcessingTask.java
│           └── util/
│               ├── ImageUtils.java
│               └── VectorUtils.java
├── input_images/          # Place input images here
├── output_images/         # Processed images output here
├── build.gradle
├── settings.gradle
├── Dockerfile
└── README.md
```

## Prerequisites

- Java 17 or higher
- Gradle 7.0 or higher
- Docker (for containerized deployment)

## Getting Started

### Local Development

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd ConcurrentImageProcessor
   ```

2. **Build the project**
   ```bash
   ./gradlew build
   ```

3. **Run the application**
   ```bash
   ./gradlew run
   ```

### Docker Deployment

1. **Build the Docker image**
   ```bash
   docker build -t concurrent-image-processor .
   ```

2. **Run the container**
   ```bash
   docker run -it --rm \
     -v $(pwd)/input_images:/app/input_images \
     -v $(pwd)/output_images:/app/output_images \
     concurrent-image-processor
   ```

   This command mounts your local `input_images` and `output_images` directories to the container.

## Usage

1. **Add input images**: Place your images in the `input_images/` directory
2. **Configure processing**: Modify configuration parameters in `ProcessingConfig.java`
3. **Run processing**: Execute the application using Gradle or Docker
4. **View results**: Processed images will be saved to `output_images/`

## Configuration

The application can be configured through the `ProcessingConfig` class:

- **Thread Pool Size**: Number of concurrent processing threads
- **Filter Types**: Available image filters and transformations
- **Tile Size**: Size of tiles for processing large images
- **Processing Parameters**: Various image processing settings

## Architecture

### Core Components

- **ImageProcessor**: Main processing engine with multi-threading support
- **TileProcessingTask**: Handles tile-based image processing
- **FilterType**: Enumeration of available image filters
- **ProcessingStats**: Performance metrics and statistics
- **ImageUtils**: Utility functions for image operations
- **VectorUtils**: Vector operations for image processing

### Processing Flow

1. **Image Loading**: Input images are loaded from the input directory
2. **Tile Creation**: Large images are divided into tiles for efficient processing
3. **Concurrent Processing**: Multiple threads process tiles simultaneously
4. **Filter Application**: Selected filters are applied to each tile
5. **Image Reconstruction**: Processed tiles are combined back into final images
6. **Output**: Processed images are saved to the output directory

## Performance

The application uses concurrent processing to maximize performance:

- **Multi-threading**: Utilizes available CPU cores for parallel processing
- **Tile-based Processing**: Reduces memory usage for large images
- **Optimized Algorithms**: Efficient image processing algorithms
- **Statistics Tracking**: Monitor processing performance and metrics

## Testing

Run the test suite:
```bash
./gradlew test
```

## Development

### Adding New Filters

1. Add filter type to `FilterType` enum
2. Implement filter logic in `ImageProcessor`
3. Update configuration if needed

### Performance Tuning

- Adjust thread pool size based on system capabilities
- Optimize tile size for memory vs. performance trade-offs
- Profile processing with different filter combinations

## Docker Commands

**Build image:**
```bash
docker build -t concurrent-image-processor .
```

**Run with volume mounts:**
```bash
docker run -it --rm \
  -v $(pwd)/input_images:/app/input_images \
  -v $(pwd)/output_images:/app/output_images \
  concurrent-image-processor
```

**Run interactive shell:**
```bash
docker run -it --rm concurrent-image-processor /bin/bash
```

## Troubleshooting

### Common Issues

1. **Out of Memory**: Reduce tile size or increase heap memory
2. **Slow Processing**: Increase thread pool size or check system resources
3. **File Permissions**: Ensure input/output directories have proper permissions

### Debug Mode

Run with debug logging:
```bash
./gradlew run --debug
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## License

[Add your license information here]

## Contact

[Add your contact information here]