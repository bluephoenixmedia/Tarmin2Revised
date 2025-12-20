import json
import traceback

try:
    with open('assets/data/items.json', 'r') as f:
        data = json.load(f)
    print("JSON is valid.")
except json.JSONDecodeError as e:
    print(f"JSON Error: {e}")
    print(f"Line: {e.lineno}, Column: {e.colno}")
    # Print context
    with open('assets/data/items.json', 'r') as f:
        lines = f.readlines()
        start = max(0, e.lineno - 5)
        end = min(len(lines), e.lineno + 5)
        for i in range(start, end):
            prefix = ">> " if i + 1 == e.lineno else "   "
            print(f"{prefix}{i+1}: {lines[i].rstrip()}")
except Exception as e:
    traceback.print_exc()
