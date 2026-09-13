// @name 31. Disable dot on double space
// Для случая когда надо удалить одно из действий

let kc_key0 = json["key-group-processors"]
    .find(kgp => kgp["key-codes"]
        .find(kc => kc === "KEYCODE_SPACE")
    );

kc_key0["on-double-press"] = kc_key0["on-double-press"].filter((a) => a["action-method-name"] !== "ActionTryDoubleSpaceDotSpaceConversion");

