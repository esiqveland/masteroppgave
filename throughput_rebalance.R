args<-commandArgs(TRUE)

library('getopt') 

spec <- matrix(c(
        'zkdata'     , 'z', 1, "character", "zookeeper and official data input file csv (required)",
        'output'    , 'o', 1, "character", "output filename (required)",
        'help'   , 'h', 0, "logical",   "this help"
),ncol=5,byrow=T)

opt = getopt(spec);

if ( !is.null(opt$help) || is.null(opt$zkdata) || is.null(opt$output) ) {
    cat(paste(getopt(spec, usage=T),"\n"));
    q(status=1);
}

#setup output
pdf(opt$output, height=5, width=10)


mydata <- read.csv(opt$zkdata)

print(mydata)

x <- mydata$Time

y1 <- mydata$CPU.Knut
y2 <- mydata$CPU.Mini

# X3selection <- which(!is.na(mydata$CPU.Eivind))
# y3 <- (mydata$CPU.Eivind[X3selection])
# x3 <- (mydata$Time[X3selection])

# X3selection <- which(!is.na(mydata$CPU.Eivind))
y3 <- (mydata$CPU.Eivind)
# x3 <- (mydata$Time[X3selection])

y4 <- mydata$OPS.s
yCPUTOT <- (mydata$CPU.Total)

f5 <- rep(1/11, 11)
y4_sym <- filter(y4, f5, sides=2)


max(y1, na.rm=TRUE)
max(y2, na.rm=TRUE)
max(y3, na.rm=TRUE)
max(y4, na.rm=TRUE)

# remove NAs
# y1[is.na(y1)] <- 0
y2[is.na(y2)] <- 1
# y3[is.na(y3)] <- 0

y2 <- filter(y2, f5, sides=2)
y1 <- filter(y1, f5, sides=2)

y6 <- y3
y6_sym <- filter(y6, f5, sides=2)

# y4_sym
# Expand right side of clipping rect to make room for the legend
#par(xpd=T, mar=par()$mar+c(0,0,0,6))
par(oma=c(0,1,0,1), xpd=FALSE)

plot(x=x, y=y2, ylim=c(0.0,1.1), 
	col='green4',
	type='l',
	xlab='Time (seconds)', ylab='CPU utilization',
	lty=3,
	#xaxt='n',
	yaxt='n', lwd=0.5,
	# xlim=c(0,50+max(x))
	# bty='U'
	)

par(new=T)
plot(x=x, y=y6_sym, ylim=c(0.0,1.1), 
	col='green4',
	type='l',
	xlab='Time (seconds)', ylab='CPU utilization',
	lty=2,
	#xaxt='n',
	yaxt='n', lwd=0.5,
	# xlim=c(0,50+max(x))
	# bty='U'
	)

par(new=T)
plot(x=x, y=y1, ylim=c(0.0,1.1), 
	col='green4',
	type='l',
	xlab='Time (seconds)', ylab='CPU utilization',
	lty=1,
	#xaxt='n',
	yaxt='n', lwd=0.5,
	# xlim=c(0,50+max(x))
	# bty='U'
	)

# par(new=T)
# plot(x=x, y=y2, ylim=c(0.0,1.1), 
# 	col='blue',
# 	type='l',
# 	xlab='Time (seconds)', ylab='CPU utilization (percentile)',
# 	lty=1,
# 	#xaxt='n',
# 	yaxt='n', lwd=1.5,
# 	axes=F,
# 	# xlim=c(0,50+max(x))
# 	# bty='U'
# 	)

# par(new=T)
# plot(x=x, y=y3, ylim=c(0.0,1.1), 
# 	col='green',
# 	type='l',
# 	xlab='Time (seconds)', ylab='CPU utilization (percentile)',
# 	lty=1,
# 	#xaxt='n',
# 	yaxt='n', lwd=1.5,
# 	axes=F,
# 	# xlim=c(0,50+max(x))
# 	# bty='U'
# 	)


# # points(x=x, y=y2, col='blue', type='l', lwd=1.0)
axis(2, pretty(c(0, 1.1)), col='blue')

par(new=T)
plot(x=x, y=y4, ylim=c(0,1.1*max(y4, na.rm=TRUE)),
 	# xlim=c(0,50+max(x)),
 	col='red', type='l', lwd=0.3,
 	xaxt='n', axes=F, ylab='')


par(new=T)
plot(x=x, y=y4_sym, ylim=c(0,1.1*max(y4, na.rm=TRUE)),
	# xlim=c(0,50+max(x)),
	col='red', type='l', lwd=2.0,
	xaxt='n', axes=F, ylab='')

# axis(4, pretty(c(0, 1.1*max(y4, na.rm=TRUE))), col='red')
axis(4, pretty(c(0, 60000)), col='red')



par(xpd=TRUE) # disable clipping
legend(
	# x=410, y=12500, 
	"bottomright", inset=c(0.005,0.01),
	legend=c('CPU (Core 2)', 'CPU (i5)', 
		'CPU (i7)', 'Throughput'), 
	col=c(rep('green4',3), rep('red', 1)), lty=c(3,1,2,1), lwd=c(0.5, 0.5, 0.5, 2.0))

par(xpd=FALSE) # clipping

# abline(v=95, col='gray60')

yMAX <- 1.1*max(y4, na.rm=TRUE)

yMIN <- 0 #0.96*yMAX

mycol <- rgb(0.75,0.75,0.75,1/4)

mydensity <- NA
polygon(x=c(105, 105, 260, 260), y=c(yMIN, yMAX, yMAX, yMIN),
     col = mycol, border = NA,
     density = mydensity
     )

polygon(x=c(400, 400, 555, 555), y=c(yMIN, yMAX, yMAX, yMIN),
     col = mycol, border = NA,
     density = mydensity
     )

polygon(x=c(595, 595, 755, 755), y=c(yMIN, yMAX, yMAX, yMIN),
     col = mycol, border = NA,
     density = mydensity
     )

polygon(x=c(795, 795, 955, 955), y=c(yMIN, yMAX, yMAX, yMIN),
     col = mycol, border = NA,
     density = mydensity
     )

polygon(x=c(995, 995, 1150, 1150), y=c(yMIN, yMAX, yMAX, yMIN),
     col = mycol, border = NA,
     density = mydensity
     )

polygon(x=c(1195, 1195, 1350, 1350), y=c(yMIN, yMAX, yMAX, yMIN),
     col = mycol, border = NA,
     density = mydensity
     )

polygon(x=c(1390, 1390, 1550, 1550), y=c(yMIN, yMAX, yMAX, yMIN),
     col = mycol, border = NA,
     density = mydensity
     )

polygon(x=c(1590, 1590, 1745, 1745), y=c(yMIN, yMAX, yMAX, yMIN),
     col = mycol, border = NA,
     density = mydensity
     )

polygon(x=c(1790, 1790, 1945, 1945), y=c(yMIN, yMAX, yMAX, yMIN),
     col = mycol, border = NA,
     density = mydensity
     )

polygon(x=c(2560, 2560, 2715, 2715), y=c(22000, yMAX, yMAX, 22000),
     col = mycol, border = NA,
     density = mydensity
     )
