package es.fdi.iw.controller;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

//import scala.annotation.meta.setter;
import es.fdi.iw.ContextInitializer;
import es.fdi.iw.model.Actividad;
import es.fdi.iw.model.User;

@Controller
public class UserController {
	private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

	@PersistenceContext
	private EntityManager entityManager;

	@RequestMapping(value = "/registro", method = RequestMethod.GET)
	public String registro(Locale locale, Model model) {
		model.addAllAttributes(HomeController.basic(locale));
		model.addAttribute("pageTitle", "Registro");
		return "registro";
	}

	@RequestMapping(value = "/usuario/crear", method = RequestMethod.GET)
	public String registro2(Locale locale, Model model) {
		model.addAllAttributes(HomeController.basic(locale));
		model.addAttribute("pageTitle", "Registro");
		model.addAttribute("prefix", "./../");
		return "registro";
	}

	/**
	 * Crear un usuario
	 */
	@RequestMapping(value = "/usuario/crear", params = { "login", "pass", "nombre", "apellido", "email",
			"passConf" }, method = RequestMethod.POST)
	@Transactional
	public String crearUsuario(@RequestParam("login") String login, @RequestParam("passConf") String passConf,
			@RequestParam("pass") String pass, @RequestParam("nombre") String nombre,
			@RequestParam("apellido") String apellido, @RequestParam("email") String email, Model model,
			HttpServletRequest request, HttpServletResponse response, HttpSession session, Locale locale) {
		String returnn = "redirect:/";
		
		model.addAllAttributes(HomeController.basic(locale));
		model.addAttribute("pageTitle", "Registro");

		if (!pass.equals(passConf)) {
			logger.info("Contraseñas fallidas: {}, {}", pass, passConf);
			model.addAttribute("error", "Las contraseñas no coinciden, verifique todos los datos.");
			returnn = "registro";
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
		if (login == null || login.length() < 4 || pass == null || pass.length() < 4 || nombre == null
				|| apellido == null || email == null) {
			model.addAttribute("error",
					"Verifique todos los campos y recuerde que el usuario y la contraseña deben tener al menos 4 caracteres.");
			returnn = "registro";
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		} else {
			User user = User.createUser(login, pass, "user", nombre, apellido, email);
			entityManager.persist(user);

			logger.info("User logged {}", user.getLogin());
			session.setAttribute("user", user);
			// sets the anti-csrf token
			getTokenForSession(session);

		}
		return returnn;
	}

	/**
	 * Crear un admin
	 * ------>>>>>  ESTE METODO ES TEMPORAL <<<<<---- HAY QUE BORRARLO AL FINALIZAR YA QUE ES INSEGURO
	 */
	@RequestMapping(value = "/usuario/crearAdmin", params = { "login", "pass", "nombre", "apellido",
			"email" }, method = RequestMethod.POST)
	@Transactional
	public String crearAdmin(@RequestParam("login") String login, @RequestParam("pass") String pass,
			@RequestParam("nombre") String nombre, @RequestParam("apellido") String apellido,
			@RequestParam("email") String email, Model model) {
		User user = User.createUser(login, pass, "admin", nombre, apellido, email);
		entityManager.persist(user);

		return "redirect:/";
	}

	/**
	 * Intercepts login requests generated by the header; then continues to load
	 * normal page
	 */

	@RequestMapping(value = "/login", method = RequestMethod.POST)
	@Transactional
	public String login(@RequestParam("login") String formLogin, @RequestParam("pass") String formPass,
			@RequestParam("source") String formSource, HttpServletRequest request, HttpServletResponse response,
			RedirectAttributes model, HttpSession session) {

		logger.info("Login attempt from '{}' while visiting '{}'", formLogin, formSource);

		// validate request
		if (formLogin == null || formLogin.length() < 4 || formPass == null || formPass.length() < 4) {
			model.addAttribute("loginError", "usuarios y contraseñas: 4 caracteres mínimo");
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		} else {
			User u = null;
			try {
				u = (User) entityManager.createNamedQuery("userByLogin").setParameter("loginParam", formLogin)
						.getSingleResult();
				if (u.isPassValid(formPass)) {
					logger.info("pass was valid");
					Actividad atv = Actividad.createActividad("Se ha conectado!", u, new Date());
					u.getActividad().add(atv);
					session.setAttribute("user", u);
					// sets the anti-csrf token
					getTokenForSession(session);
				} else {
					logger.info("pass was NOT valid");
					session.setAttribute("loginError", "error en usuario o contraseña");
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				}
			} catch (NoResultException nre) {
				// if (formPass.length() == 4) {
				// UGLY: register new users if they do not exist and pass is 4
				// chars long
				// logger.info("no-such-user; creating user {}", formLogin);
				// User user = User.createUser(formLogin, formPass, "user");
				// entityManager.persist(user);
				// session.setAttribute("user", user);
				// sets the anti-csrf token
				// getTokenForSession(session);
				// } else {
				logger.info("no such login: {}", formLogin);
				// }
				session.setAttribute("loginError", "error en usuario o contraseña");
			}
		}

		// redirects to view from which login was requested
		/*
		 * CONSIDER TO UNUSE formSource or try to find another solution... like filtering formSource in a valid list of 
		 * valid sources
		 */
		return "redirect:" + formSource;
	}
	
	@RequestMapping(value = "/perfil", method = RequestMethod.GET)
	public String perfil(Locale locale, Model model, HttpSession session) {
		model.addAllAttributes(HomeController.basic(locale));
		model.addAttribute("pageTitle", "Perfil");
		String returnn = "perfil";
		if(!ping(session)){
			returnn = "redirect:home";
		} else {
			User u = (User) session.getAttribute("user");
			model.addAttribute("amigos", entityManager.createNamedQuery("allAmigos").setParameter("userParam", u).getResultList());
			//model.addAttribute("actividad", entityManager.createNamedQuery("allActividadByUser").setParameter("userParam", u).getResultList()); // Devuelve TODA la actividad del usuario 
			model.addAttribute("actividad", u.getActividad()); // Devuelve la actividad de la session actual
		}
		return returnn;
	}

	
	@RequestMapping(value = {"/user/{id}", "/perfil/{id}"}, method = RequestMethod.GET)
	public String user(@PathVariable("id") long id, HttpServletResponse response, Model model, Locale locale, HttpSession session) {
		String returnn = "perfil";
		model.addAllAttributes(HomeController.basic(locale));
		model.addAttribute("pageTitle", "Perfil");
		model.addAttribute("prefix", "./../");
		User us = entityManager.find(User.class, id);
		if (us == null) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			logger.error("No such user: {}", id);
			returnn = "redirect:/";
		} else {
			model.addAttribute("user", us);
			
			User u = (User) session.getAttribute("user");
			if (u != null) {
				Actividad atv = Actividad.createActividad("Ha visitado el perfil de " + us.getName() + " " + us.getLname(), u, new Date());
				u.getActividad().add(atv);
			}
		}
		return returnn;
	}

	/**
	 * Logout (also returns to home view).
	 */
	@RequestMapping(value = "/logout", method = RequestMethod.GET)
	public String logout(HttpSession session) {
		User u = (User) session.getAttribute("user");
		if (u != null) {
			Actividad natv = Actividad.createActividad("Se ha desconectado", u, new Date());
			u.getActividad().add(natv);
		
			logger.info("User '{}' logged out", u.getEmail());
			session.invalidate();
		}
		return "redirect:home";
	}

	/**
	 * Uploads a photo for a user
	 * 
	 * @param id
	 *            of user
	 * @param photo
	 *            to upload
	 * @return
	 */
	@RequestMapping(value = "/user", method = RequestMethod.POST)
	public @ResponseBody String handleFileUpload(@RequestParam("photo") MultipartFile photo,
			@RequestParam("id") String id) {
		if (!photo.isEmpty()) {
			try {
				byte[] bytes = photo.getBytes();
				BufferedOutputStream stream = new BufferedOutputStream(
						new FileOutputStream(ContextInitializer.getFile("user", id)));
				stream.write(bytes);
				stream.close();
				return "You successfully uploaded " + id + " into "
						+ ContextInitializer.getFile("user", id).getAbsolutePath() + "!";
			} catch (Exception e) {
				return "You failed to upload " + id + " => " + e.getMessage();
			}
		} else {
			return "You failed to upload a photo for " + id + " because the file was empty.";
		}
	}

	// /**
	// * Displays user details
	// */
	// @RequestMapping(value = "/user", method = RequestMethod.GET)
	// public String user(HttpSession session, HttpServletRequest request) {
	// return "user";
	// }

	/**
	 * Returns a users' photo
	 * 
	 * @param id
	 *            id of user to get photo from
	 * @return
	 */
	@ResponseBody
	@RequestMapping(value = "/user/photo", method = RequestMethod.GET, produces = MediaType.IMAGE_JPEG_VALUE)
	public byte[] userPhoto(@RequestParam("id") String id) throws IOException {
		File f = ContextInitializer.getFile("user", id); // Cambiar "user" e id
															// para pedir otros
															// archivos
		InputStream in = null;
		if (f.exists()) {
			in = new BufferedInputStream(new FileInputStream(f));
		} else {
			in = new BufferedInputStream(this.getClass().getClassLoader().getResourceAsStream("unknown-user.jpg"));
		}

		return IOUtils.toByteArray(in);
	}

	/**
	 * userPhoto Toggles debug mode
	 */
	@RequestMapping(value = "/debug", method = RequestMethod.GET)
	public String debug(HttpSession session, HttpServletRequest request) {
		String formDebug = request.getParameter("debug");
		logger.info("Setting debug to {}", formDebug);
		session.setAttribute("debug", "true".equals(formDebug) ? "true" : "false");
		return "redirect:/";
	}

	/*
	 * Returns true if the user is logged in
	 */
	static boolean ping(HttpSession session) {
		boolean returnn = false;
		User u = (User) session.getAttribute("user");
		if (u != null) {
			returnn = true;
		}
		return returnn;
	}

	/**
	 * Returns true if the user is logged in and is an admin
	 */
	static boolean isAdmin(HttpSession session) {
		boolean returnn = false;
		User u = (User) session.getAttribute("user");
		if (u != null) {
			returnn = u.getRole().equals("admin");
		}
		return returnn;
	}

	/**
	 * Checks the anti-csrf token for a session against a value
	 * 
	 * @param session
	 * @param token
	 * @return the token
	 */
	static boolean isTokenValid(HttpSession session, String token) {
		Object t = session.getAttribute("csrf_token");
		return (t != null) && t.equals(token);
	}

	/**
	 * Returns an anti-csrf token for a session, and stores it in the session
	 * 
	 * @param session
	 * @return
	 */
	static String getTokenForSession(HttpSession session) {
		String token = UUID.randomUUID().toString();
		session.setAttribute("csrf_token", token);
		return token;
	}
}