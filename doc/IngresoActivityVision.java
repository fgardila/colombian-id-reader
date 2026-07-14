package com.code93.registrocrm;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.code93.registrocrm.modelo.Acceder;
import com.code93.registrocrm.modelo.DataRegistroPreguntas;
import com.code93.registrocrm.modelo.ModeloIngreso;
import com.code93.registrocrm.modelo.OpcionesRegistroPreguntas;
import com.code93.registrocrm.vision.BarcodeCaptureActivity;
import com.code93.registrocrm.volley.VolleyApplication;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dmax.dialog.SpotsDialog;

import static com.code93.registrocrm.LoginActivity.acceder;
import static com.code93.registrocrm.LoginActivity.accessPoint;
import static com.code93.registrocrm.LoginActivity.registroPreguntas;

public class IngresoActivityVision extends AppCompatActivity {

    private static final int RC_BARCODE_CAPTURE = 9001;
    private static final String TAG = "BarcodeMain";

    TextInputEditText etNombre;
    TextInputEditText etApellidos;
    TextInputEditText etCedula;
    TextInputEditText etFechaNacimeinto;
    TextInputEditText etRh;
    TextInputEditText etCelular;
    TextInputEditText etTemperatura;
    TextInputEditText etMarcas;
    TextInputEditText etExcepcion;

    TextInputLayout tilTemperatura;

    RadioButton radioFemenino;
    RadioButton radioMasculino;

    MaterialCheckBox checkbox;

    android.app.AlertDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ingreso);

        dialog = new SpotsDialog.Builder().setContext(this).build();

        etNombre = findViewById(R.id.etNombre);
        etApellidos = findViewById(R.id.etApellidos);
        etCedula = findViewById(R.id.etCedula);
        etFechaNacimeinto = findViewById(R.id.etFechaNacimeinto);
        etRh = findViewById(R.id.etRh);
        etCelular = findViewById(R.id.etCelular);
        etTemperatura = findViewById(R.id.etTemperatura);
        etMarcas = findViewById(R.id.etMarcas);
        etExcepcion = findViewById(R.id.etExcepcion);

        tilTemperatura = findViewById(R.id.tilTemperatura);

        radioFemenino = findViewById(R.id.radioFemenino);
        radioMasculino = findViewById(R.id.radioMasculino);

        checkbox = findViewById(R.id.checkbox);

        initToolbar();

    }

    private void initToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Registro entrada");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
    }

    private void validacionDeCampos() {
        ModeloIngreso modeloIngreso = new ModeloIngreso();
        modeloIngreso.setNombre(Objects.requireNonNull(etNombre.getText()).toString());
        modeloIngreso.setApellido(Objects.requireNonNull(etApellidos.getText()).toString());
        modeloIngreso.setCedula(Objects.requireNonNull(etCedula.getText()).toString());
        modeloIngreso.setFechaNacimiento(Objects.requireNonNull(etFechaNacimeinto.getText()).toString());
        modeloIngreso.setCelular(Objects.requireNonNull(etCelular.getText().toString()));
        modeloIngreso.setTemperatura(Objects.requireNonNull(etTemperatura.getText().toString()));
        if (selectExcepcion != null) {
            modeloIngreso.setExcepcion(Objects.requireNonNull(selectExcepcion.getOpcId()));
        } else {
            modeloIngreso.setExcepcion("");
        }


        if (radioFemenino.isChecked()) {
            modeloIngreso.setGenero("F");
        } else if (radioMasculino.isChecked()) {
            modeloIngreso.setGenero("M");
        } else {
            modeloIngreso.setGenero("");
        }

        StringBuilder locals = new StringBuilder();
        if (selectLocales != null) {
            if (!selectLocales.isEmpty()) {
                for (OpcionesRegistroPreguntas loc : selectLocales) {
                    locals.append(loc.getOpcId());
                    locals.append(",");
                }
                modeloIngreso.setLocales(locals.toString().substring(0, locals.toString().length() - 1));
            } else {
                modeloIngreso.setLocales("");
            }
        } else {
            modeloIngreso.setLocales("");
        }

        if (checkbox.isChecked()){
            modeloIngreso.setAceptacionDatos("1");
        } else {
            modeloIngreso.setAceptacionDatos("0");
        }

        boolean camposCompletos = true;
        if (modeloIngreso.getNombre().isEmpty()) {
            etNombre.setError("Campo requerido");
            camposCompletos = false;
        }
        if (modeloIngreso.getApellido().isEmpty()) {
            etApellidos.setError("Campo requerido");
            camposCompletos = false;
        }
        if (modeloIngreso.getCedula().isEmpty()) {
            etCedula.setError("Campo requerido");
            camposCompletos = false;
        }
        if (modeloIngreso.getTemperatura().isEmpty()) {
            tilTemperatura.setError("Campo requerido");
            camposCompletos = false;
        }

        if (camposCompletos) {
            if (validarTemperatura(modeloIngreso.getTemperatura())) {
                if (modeloIngreso.getAceptacionDatos().equals("1")) {
                    enviarTransaccionIngreso(modeloIngreso);
                } else {
                    dialog.dismiss();
                    Tools.showDialogError(this, "Debe aceptar el tratamiento de la politica de datos.");
                }
            } else {
                dialog.dismiss();
                Tools.showDialogAlert(this);
            }
        } else {
            dialog.dismiss();
        }

    }

    private boolean validarTemperatura(String sTemp) {
        if (sTemp != null) {
            if (sTemp.contains(",")){
                sTemp = sTemp.replaceAll(",", ".");
            }
            try {
                double temp = Double.parseDouble(sTemp);
                if (temp < 38)
                    return true;
                else {
                    return false;
                }
            } catch (NumberFormatException e){
                return false;
            }
        } else {
            return false;
        }
    }

    private void enviarTransaccionIngreso(ModeloIngreso modeloIngreso) {
        StringBuilder url  = new StringBuilder("https://cc.wegrowcrm.com/api/v2/Registro/entrada");
        if (accessPoint.getPointId() != null) {
            url.append("?punId=").append(accessPoint.getPointId());
        } else {
            url.append("?punId=").append("1");
        }
        url.append("&conNdoc=").append(modeloIngreso.getCedula());
        url.append("&conNom=").append(modeloIngreso.getNombre());
        url.append("&conApe=").append(modeloIngreso.getApellido());
        url.append("&conGen=").append(modeloIngreso.getGenero());
        url.append("&conFnac=").append(modeloIngreso.getFechaNacimiento());
        url.append("&locales=").append(modeloIngreso.getLocales());
        url.append("&concel=").append(modeloIngreso.getCelular());
        url.append("&conTemp=").append(modeloIngreso.getTemperatura().replaceAll(".", ","));
        url.append("&conExcepcion=").append(modeloIngreso.getExcepcion());
        url.append("&conPolDatos=").append(modeloIngreso.getAceptacionDatos());

        Log.e("keshav", "Registro/Ingreso URL --> " + url.toString());

        JsonObjectRequest jsonOblect = new JsonObjectRequest(Request.Method.POST, url.toString(), null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                dialog.dismiss();
                Log.e("keshav", "Registro/Ingreso --> " + response.toString());
                Gson gson = new Gson();
                Acceder acceder;
                acceder = gson.fromJson(response.toString(), Acceder.class);
                boolean success = acceder.getSuccess();
                if (success) {
                    showDialogPositive("Registro exitoso", MainActivity.class);
                } else {
                    Tools.showDialogError(IngresoActivityVision.this, acceder.getError());
                }
            }
        }, error -> {
            dialog.dismiss();
            Log.e("TAG", "Volley onErrorResponse - " + error.getMessage());
            //Tools.showDialogError(IngresoActivity.this, "No llego respuesta -> " + error.getMessage());

        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                final Map<String, String> headers = new HashMap<>();
                headers.put("X-Auth-Token", "api-key " + acceder.getDataAcceder().getApiKey());//put your token here
                return headers;
            }
        };
        VolleyApplication.getInstance(this).addToRequestQueue(jsonOblect);
    }

    public void scanCedula(View view) {
        if (view.getId() == R.id.btnScan) {
            Intent intent = new Intent(this, BarcodeCaptureActivity.class);
            intent.putExtra(BarcodeCaptureActivity.AutoFocus, true);
            intent.putExtra(BarcodeCaptureActivity.UseFlash, true);
            startActivityForResult(intent, RC_BARCODE_CAPTURE);
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                    Log.d(TAG, "Barcode read: " + barcode.displayValue);
                    try {
                        parseDataCode(barcode.displayValue);
                    } catch (StringIndexOutOfBoundsException e) {
                        Tools.showDialogError(this, "Error al obtener información del codigo de barras");
                    }
                } else {
                    Tools.showDialogError(this, "Error al obtener información del codigo de barras");
                }
            } else {
                Log.d(TAG, "No barcode captured, intent data is NOT SUCCESS");
               /* statusMessage.setText(String.format(getString(R.string.barcode_error),
                        CommonStatusCodes.getStatusCodeString(resultCode)));*/
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void parseDataCode(String barcode) throws StringIndexOutOfBoundsException {
        InfoTarjeta infoTarjeta = null;
        if (barcode != null) {
            if (barcode.length() < 150) {
                Tools.showDialogError(this, "Error al obtener información del codigo de barras");
            } else {
                infoTarjeta = new InfoTarjeta();
                String primerApellido = "", segundoApellido = "", primerNombre = "", segundoNombre = "", cedula = "", rh = "", fechaNacimiento = "", sexo = "";

                String alphaAndDigits = barcode.replaceAll("[^\\p{Alpha}\\p{Digit}\\+\\_]+", " ");
                String[] splitStr = alphaAndDigits.split("\\s+");

                if (!alphaAndDigits.contains("PubDSK")) {
                    int corrimiento = 0;


                    Pattern pat = Pattern.compile("[A-Z]");
                    Matcher match = pat.matcher(splitStr[2 + corrimiento]);
                    int lastCapitalIndex = -1;
                    if (match.find()) {
                        lastCapitalIndex = match.start();
                        String TAG = "parseDataCode";
                        Log.d(TAG, "match.start: " + match.start());
                        Log.d(TAG, "match.end: " + match.end());
                        Log.d(TAG, "splitStr: " + splitStr[2 + corrimiento]);
                        Log.d(TAG, "splitStr length: " + splitStr[2 + corrimiento].length());
                        Log.d(TAG, "lastCapitalIndex: " + lastCapitalIndex);
                    }
                    cedula = splitStr[2 + corrimiento].substring(lastCapitalIndex - 10, lastCapitalIndex);
                    primerApellido = splitStr[2 + corrimiento].substring(lastCapitalIndex);
                    segundoApellido = splitStr[3 + corrimiento];
                    primerNombre = splitStr[4 + corrimiento];
                    /**
                     * Se verifica que contenga segundo nombre
                     */
                    if (Character.isDigit(splitStr[5 + corrimiento].charAt(0))) {
                        corrimiento--;
                    } else {
                        segundoNombre = splitStr[5 + corrimiento];
                    }

                    //sexo = splitStr[6 + corrimiento].contains("M") ? "Masculino" : "Femenino";
                    sexo = splitStr[6 + corrimiento];
                    rh = splitStr[6 + corrimiento].substring(splitStr[6 + corrimiento].length() - 2);
                    fechaNacimiento = splitStr[6 + corrimiento].substring(2, 10);

                } else {
                    int corrimiento = 0;
                    Pattern pat = Pattern.compile("[A-Z]");
                    if (splitStr[2 + corrimiento].length() > 7) {
                        corrimiento--;
                    }


                    Matcher match = pat.matcher(splitStr[3 + corrimiento]);
                    int lastCapitalIndex = -1;
                    if (match.find()) {
                        lastCapitalIndex = match.start();

                    }

                    cedula = splitStr[3 + corrimiento].substring(lastCapitalIndex - 10, lastCapitalIndex);
                    primerApellido = splitStr[3 + corrimiento].substring(lastCapitalIndex);
                    segundoApellido = splitStr[4 + corrimiento];
                    if (splitStr[5 + corrimiento].startsWith("0")){ // UN NOMBRE UN APELLIDO
                        segundoApellido = " ";
                        primerNombre = splitStr[4 + corrimiento];
                        sexo = splitStr[5 + corrimiento].contains("M") ? "M" : "F";
                        rh = splitStr[5 + corrimiento].substring(splitStr[5 + corrimiento].length() - 2);
                        fechaNacimiento = splitStr[5 + corrimiento].substring(2, 10);
                    } else if (splitStr[6 + corrimiento].startsWith("0")){ // DOS APELLIDOS UN NOMBRE
                        primerNombre = splitStr[5 + corrimiento];
                        segundoNombre = " ";
                        sexo = splitStr[6 + corrimiento].contains("M") ? "M" : "F";
                        rh = splitStr[6 + corrimiento].substring(splitStr[6 + corrimiento].length() - 2);
                        fechaNacimiento = splitStr[6 + corrimiento].substring(2, 10);
                    } else { //DOS APELLIDOS DOS NOMBRES
                        primerNombre = splitStr[5 + corrimiento];
                        segundoNombre = splitStr[6 + corrimiento];
                        sexo = splitStr[7 + corrimiento].contains("M") ? "M" : "F";
                        rh = splitStr[7 + corrimiento].substring(splitStr[7 + corrimiento].length() - 2);
                        fechaNacimiento = splitStr[7 + corrimiento].substring(2, 10);
                    }


                }

                String tagParser = "parseDataCode";
                Log.d(tagParser, "Nombre: " + primerNombre + segundoNombre) ;
                Log.d(tagParser, "CEDULA: " + cedula) ;
                Log.d(tagParser, "sexo: " + sexo) ;
                infoTarjeta.setPrimerNombre(primerNombre);
                infoTarjeta.setSegundoNombre(segundoNombre);
                infoTarjeta.setPrimerApellido(primerApellido);
                infoTarjeta.setSegundoApellido(segundoApellido);
                infoTarjeta.setCedula(cedula);
                infoTarjeta.setSexo(sexo);
                infoTarjeta.setFechaNacimiento(fechaNacimiento);
                infoTarjeta.setRh(rh);
                actualizarCampos(infoTarjeta);
            }

        } else {
            Log.d("TAG", "No barcode capturado");
        }
    }

    private void actualizarCampos(InfoTarjeta infoTarjeta) {
        etNombre.setText(String.format("%s %s", infoTarjeta.getPrimerNombre(), infoTarjeta.getSegundoNombre()));
        etApellidos.setText(String.format("%s %s", infoTarjeta.getPrimerApellido(), infoTarjeta.getSegundoApellido()));
        etCedula.setText(Tools.eliminarCerosIzquierda(infoTarjeta.getCedula()));
        etFechaNacimeinto.setText(infoTarjeta.getFechaNacimiento());
        etRh.setText(infoTarjeta.getRh());
        if (infoTarjeta.getSexo().contains("M")){
            radioMasculino.setChecked(true);
            radioFemenino.setChecked(false);
        } else {
            radioFemenino.setChecked(true);
            radioMasculino.setChecked(false);
        }
    }

    private int selectLocal;
    List<OpcionesRegistroPreguntas> selectLocales = null;
    List<OpcionesRegistroPreguntas> locales = null;
    private boolean[] clickedLocales = null;
    public void listaLocales(View view) {
        if (view != null) {
            for (DataRegistroPreguntas data : registroPreguntas.getData()) {
                if (data.getPreId().equals("locales")) {
                    locales = data.getOpciones();
                }
            }
            try {
                String[] strLocales = new String[locales.size()];
                for (int i=0; i < locales.size(); i++) {
                    OpcionesRegistroPreguntas excepcion = locales.get(i);
                    strLocales[i] = excepcion.getOpcNom();
                }
                clickedLocales = new boolean[locales.size()];
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Ubicación Punto Acceso");
                builder.setMultiChoiceItems(strLocales, clickedLocales, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i, boolean b) {
                        clickedLocales[i] = b;
                    }
                });
                builder.setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        selectLocales = new ArrayList<OpcionesRegistroPreguntas>();
                        int pos = 0;
                        for (int x=0; x < locales.size(); x++) {
                            if (clickedLocales[x] == true) {
                                selectLocales.add(locales.get(x));
                                pos ++;
                            }
                        }
                        actualizarLocales(selectLocales);
                        /*selectExcepcion = excepciones.get(selectAccess);
                        */
                    }
                });
                builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        actualizarExcepcion("");
                    }
                });
                builder.show();
            } catch (NullPointerException e) {
                Toast.makeText(this, "No se encuentran excepciones.", Toast.LENGTH_SHORT).show();
            }

        }
    }

    private void actualizarLocales(List<OpcionesRegistroPreguntas> selectLocales) {
        StringBuilder texto = new StringBuilder();
        for (OpcionesRegistroPreguntas loc : selectLocales) {
            texto.append(loc.getOpcNom());
            texto.append(", ");
        }
        etMarcas.setText(texto.toString());

    }

    private int selectAccess;
    OpcionesRegistroPreguntas selectExcepcion = null;
    List<OpcionesRegistroPreguntas> excepciones = null;
    public void listaExcepciones(View view) {
        if (view != null) {
            for (DataRegistroPreguntas data : registroPreguntas.getData()) {
                if (data.getPreId().equals("conExcepcion")) {
                    excepciones = data.getOpciones();
                }
            }
            try {
                String[] strExepcion = new String[excepciones.size()];
                for (int i=0; i < excepciones.size(); i++) {
                    OpcionesRegistroPreguntas excepcion = excepciones.get(i);
                    strExepcion[i] = excepcion.getOpcNom();
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Ubicación Punto Acceso");
                builder.setSingleChoiceItems(strExepcion, 0, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        selectAccess = i;
                    }
                });
                builder.setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        selectExcepcion = excepciones.get(selectAccess);
                        actualizarExcepcion(selectExcepcion.getOpcNom());
                    }
                });
                builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        actualizarExcepcion("");
                    }
                });
                builder.show();
            } catch (NullPointerException e) {
                Toast.makeText(this, "No se encuentran excepciones.", Toast.LENGTH_SHORT).show();
            }

        }
    }

    private void actualizarExcepcion(String texto) {
        etExcepcion.setText(texto);
    }


    public void registrarIngreso(View view) {
        if (view != null) {
            dialog.setMessage("Conectando...");
            dialog.show();
            validacionDeCampos();
        }
    }

    private void showDialogPositive(String messaje, Class<MainActivity> goTo) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); // before
        dialog.setContentView(R.layout.dialog_positive);
        dialog.setCancelable(false);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;

        TextView content = dialog.findViewById(R.id.content);
        content.setText(messaje);

        ((MaterialButton) dialog.findViewById(R.id.bt_close)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Toast.makeText(context, ((MaterialButton) v).getText().toString() + " Clicked", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(IngresoActivityVision.this, goTo));
                dialog.dismiss();
            }
        });

        dialog.show();
        dialog.getWindow().setAttributes(lp);
    }
}