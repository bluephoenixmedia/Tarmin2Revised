import json
import os
import glob

def check_file(path):
    # print(f"Checking {os.path.basename(path)}")
    try:
        with open(path, 'r') as f:
            data = json.load(f)
            
        errors_found = False
        for key, entry in data.items():
            if "spriteData" in entry:
                sprites = entry["spriteData"]
                if not isinstance(sprites, list):
                    continue
                for i, line in enumerate(sprites):
                    if len(line) != 24:
                        print(f"ERROR: {key} in {os.path.basename(path)} index {i}: length {len(line)}")
                        errors_found = True
                        
        if not errors_found:
            print(f"CLEAN: {os.path.basename(path)}")
            
    except Exception as e:
        print(f"Failed to process {path}: {e}")

def main():
    base_dir = r"c:\Users\denni\Desktop\Tarmin2\assets\data"
    files = glob.glob(os.path.join(base_dir, "*.json"))
    
    for filename in files:
        check_file(filename)

if __name__ == "__main__":
    main()
