import json
import os

def main():
    json_path = 'assets/data/weapons.json'
    
    with open(json_path, 'r') as f:
        data = json.load(f)

    updates = {
        "RUSTY_SWORD": "images/weapons/rusty_sword.png",
        "ARROW_DAIKYU": "images/weapons/arrow_daikyu.png",
        "BAGH_NAKH_TIGER_CLAWS": "images/weapons/bagh_nakh_tiger_claws.png",
        "BARD_S_FRIEND": "images/weapons/bard_s_friend.png",
        "BASILARD": "images/weapons/basilard.png",
        "BO_STICK": "images/weapons/bo_stick.png",
        "BOW_COMPOSITE_LONG": "images/weapons/bow_composite_long.png",
        "CHAIN": "images/weapons/chain.png",
        "CROSSBOW_DOUBLED": "images/weapons/crossbow_doubled.png",
        "DRAGON_S_PAW": "images/weapons/dragon_s_paw.png",
        "JAVELIN_ONE_HANDED": "images/weapons/javelin_one_handed.png",
        "PICK_FOOTMAN_S": "images/weapons/pick_footman_s.png",
        "POLEARM_AWL_PIKE3": "images/weapons/polearm_awl_pike3.png",
        "POLEARM_BARDICHE": "images/weapons/polearm_bardiche.png",
        "POLEARM_BEC_DE_CORBIN": "images/weapons/polearm_bec_de_corbin.png",
        "POLEARM_BILL_GUISARME": "images/weapons/polearm_bill_guisarme.png"
    }
    
    updated_count = 0
    
    for key, new_path in updates.items():
        if key in data:
            old_path = data[key].get('texturePath')
            if old_path != new_path:
                print(f"Updating {key}: {old_path} -> {new_path}")
                data[key]['texturePath'] = new_path
                updated_count += 1
            else:
                print(f"Skipping {key}: Already correct")
        else:
            print(f"WARNING: Key {key} not found in weapons.json")

    if updated_count > 0:
        with open(json_path, 'w') as f:
            json.dump(data, f, indent='\t') # Detected tab indentation from previous views
        print(f"Successfully updated {updated_count} entries.")
    else:
        print("No updates needed.")

if __name__ == '__main__':
    main()
