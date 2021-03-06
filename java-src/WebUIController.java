import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javafx.event.EventHandler;
import javafx.scene.input.KeyEvent;

/**
 *
 * @author Alvin Tabontabon
 */
public class WebUIController implements Initializable {

  @FXML
  TextField txtURL;
  @FXML
  WebView webView;
  private WebEngine webEngine;
  public static WebEngine engine;
  public static WebView view;

  @FXML
  private void goAction(ActionEvent evt) {
    webEngine.load(txtURL.getText().startsWith("http") ? txtURL.getText() : "http://" + txtURL.getText());
  }

  @Override
  public void initialize(URL url, ResourceBundle rb) {

    webEngine = webView.getEngine();
    engine = webEngine;
    view = webView;
    webEngine.locationProperty().addListener(new ChangeListener<String>() {
        @Override
        public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
          txtURL.setText(newValue);
        }
      });
    txtURL.setText("http://ahungry.com");

    view.setOnKeyPressed(new EventHandler<KeyEvent>() {
        @Override
        public void handle(KeyEvent event)
        {
          System.out.println("Key pushed\n");
          // currentlyActiveKeys.add(event.getCode().toString());
        }
      });
  }
}
