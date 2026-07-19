import sys

def check_braces(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    depth = 0
    for i, line in enumerate(lines):
        for char in line:
            if char == '{':
                depth += 1
            elif char == '}':
                depth -= 1
        
        if line.startswith('fun ') or line.startswith('private fun '):
            print(f"Line {i+1} function starts. Depth before: {depth}")
            
    print(f"Final depth: {depth}")

check_braces(sys.argv[1])
