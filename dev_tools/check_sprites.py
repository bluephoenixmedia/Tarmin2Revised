import json
import os
import glob

def check_file(path):
    print(f"--- START Checking {os.path.basename(path)} ---")
    try:
        with open(path, 'r') as f:
            data = json.load(f)
            
        count = 0
        errors = 0
        for key, entry in data.items():
            if "spriteData" in entry:
                sprites = entry["spriteData"]
                if not isinstance(sprites, list):
                    continue
                count += 1
                for i, line in enumerate(sprites):
                    if len(line) != 24:
                        print(f"ERROR: {key} in {path} has invalid sprite line length at index {i}: {len(line)} chars.")
                        # print(f"Line content: '{line}'") 
                        errors += 1
        
        print(f"--- END Checking {os.path.basename(path)}. Checked {count} entities. {errors} errors found. ---")
                        
    except Exception as e:
        print(f"Failed to process {path}: {e}")

def main():
    base_dir = r"c:\Users\denni\Desktop\Tarmin2\assets\data"
    files = glob.glob(os.path.join(base_dir, "*.json"))
    print(f"Found files: {files}")
    
    for filename in files:
        check_file(filename)

if __name__ == "__main__":
    main()
