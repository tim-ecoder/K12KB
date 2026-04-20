// @name 13. Move Shift+Enter action to Alt+Enter
// Переносит действие с Shift+Enter на Alt+Enter

let enter_osp = json["key-group-processors"]
    .find(kgp => kgp["key-codes"]
        .find(kc => kc === "KEYCODE_ENTER"))
    ["on-short-press"];

let shift_act = enter_osp.find(
    act => act["meta-mode-method-names"]
        && act["meta-mode-method-names"]
            .find(mmmn => mmmn === "MetaIsShiftPressed"));

shift_act["meta-mode-method-names"] = shift_act["meta-mode-method-names"]
    .map(mmmn => mmmn === "MetaIsShiftPressed" ? "MetaIsAltPressed" : mmmn);
