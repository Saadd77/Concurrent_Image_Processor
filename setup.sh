#!/bin/bash

# Setup script for ConcurrentImageProcessor
echo "Setting up ConcurrentImageProcessor for GitHub submission..."

# Create necessary directories
echo "Creating directories..."
mkdir -p input_images
mkdir -p output_images
mkdir -p .github/workflows

# Create a sample README for input_images directory
cat > input_images/README.md << 'EOF'
# Input Images Directory

Place your input images here for processing.

Supported formats:
- JPEG (.jpg, .jpeg)
- PNG (.png)
- GIF (.gif)
- BMP (.bmp)
- TIFF (.tiff)

Example usage:
1. Copy your images to this directory
2. Run the application
3. Check the output_images directory for results
EOF

# Create a sample README for output_images directory
cat > output_images/README.md << 'EOF'
# Output Images Directory

Processed images will be saved here.

The application will create subdirectories based on:
- Processing date/time
- Filter types applied
- Processing parameters used

Results include:
- Processed images
- Processing statistics
- Performance metrics
EOF

# Make gradlew executable if it exists
if [ -f "gradlew" ]; then
    echo "Making gradlew executable..."
    chmod +x gradlew
fi

# Initialize git repository if not already initialized
if [ ! -d ".git" ]; then
    echo "Initializing Git repository..."
    git init
    git add .
    git commit -m "Initial commit: ConcurrentImageProcessor setup"
else
    echo "Git repository already initialized."
fi

echo "Setup complete!"
echo ""
echo "Next steps:"
echo "1. Review and customize the configuration files"
echo "2. Add your input images to the input_images/ directory"
echo "3. Test the application locally:"
echo "   ./gradlew build"
echo "   ./gradlew run"
echo "4. Test Docker build:"
echo "   docker build -t concurrent-image-processor ."
echo "5. Create GitHub repository and push:"
echo "   git remote add origin <your-repo-url>"
echo "   git branch -M main"
echo "   git push -u origin main"
echo ""
echo "Docker commands:"
echo "- Build: docker build -t concurrent-image-processor ."
echo "- Run: docker run -it --rm -v \$(pwd)/input_images:/app/input_images -v \$(pwd)/output_images:/app/output_images concurrent-image-processor"
echo "- Or use: docker-compose up"