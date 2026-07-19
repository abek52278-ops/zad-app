import sys

def check_indentation(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    depth = 0
    for i, line in enumerate(lines):
        stripped = line.strip()
        if not stripped or stripped.startswith("//"):
            continue
            
        # Count spaces before first non-space char
        indent = len(line) - len(line.lstrip(' '))
        
        # If line starts with }, depth decreases before check
        temp_depth = depth
        if stripped.startswith('}'):
            temp_depth -= 1
            
        if indent != temp_depth * 4 and temp_depth > 0:
            print(f"Line {i+1}: expected indent {temp_depth*4}, got {indent}. Line: {stripped[:50]}")
            
        for char in stripped:
            if char == '{':
                depth += 1
            elif char == '}':
                depth -= 1

check_indentation(sys.argv[1])
