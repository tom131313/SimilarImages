@echo off
rem usage:imageEdit "E:\Pictures\Fair 7-20-09_39.jpg" "E:\Pictures\Vicki's camera\vicki camera 067.jpg"
start "" "C:\Program Files\IrfanView\i_view64.exe" %1
start "" /WAIT "C:\Program Files\IrfanView\i_view64.exe" %2