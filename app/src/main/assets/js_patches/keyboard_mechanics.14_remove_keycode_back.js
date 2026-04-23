// @name 14. Remove KEYCODE_BACK handling
// Удаляет обработчик клавиши BACK из key-group-processors

let kgps = json["key-group-processors"];

kgps.splice(
    kgps.findIndex(kgp => kgp["key-codes"]
        .find(kc => kc === "KEYCODE_BACK")),
    1);
